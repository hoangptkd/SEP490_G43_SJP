package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewConversationTurn;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.enums.AnswerAnalysisAction;
import com.sjp.recruitment.model.enums.InterviewDialogueState;
import com.sjp.recruitment.model.enums.InterviewTurnAnswerStatus;
import com.sjp.recruitment.model.enums.InterviewTurnType;
import com.sjp.recruitment.repository.InterviewAnswerRepository;
import com.sjp.recruitment.repository.InterviewConversationTurnRepository;
import com.sjp.recruitment.repository.InterviewQuestionRepository;
import com.sjp.recruitment.repository.InterviewSessionRepository;
import com.sjp.recruitment.service.ai.AiProviderException;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiInterviewConversationService {

    public static final String ERROR_STAGE_ANALYSIS = "ANALYZE_ANSWER";
    public static final String ERROR_STAGE_ADAPTIVE = "ADAPTIVE_QUESTIONS";
    public static final String ERROR_STAGE_EVALUATION = "FINAL_EVALUATION";

    private final AiInterviewProperties properties;
    private final CandidateService candidateService;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final InterviewConversationTurnRepository turnRepository;
    private final ShopAiKeyClient shopAiKeyClient;
    private final AiInterviewConversationPolicy policy;
    private final AiInterviewConversationTemplateBank templates;
    private final TransactionTemplate transactions;

    @Transactional
    public void initialize(InterviewSession session) {
        if (session.getDialogueState() != null) {
            return;
        }
        List<InterviewQuestion> questions = questionRepository
                .findBySessionIdOrderByOrderIndexAsc(session.getId());
        if (questions.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "INTERVIEW_QUESTIONS_NOT_READY",
                    "Chưa có câu hỏi CORE để bắt đầu hội thoại.");
        }

        session.setDialogueState(InterviewDialogueState.SESSION_START);
        InterviewConversationTurn opening = newTurn(
                session,
                null,
                null,
                InterviewTurnType.OPENING,
                templates.opening(targetRole(session)),
                InterviewTurnAnswerStatus.NOT_REQUIRED
        );
        transition(session, InterviewDialogueState.OPENING);
        InterviewConversationTurn core = newTurn(
                session,
                questions.get(0),
                opening,
                InterviewTurnType.CORE_QUESTION,
                questions.get(0).getContent(),
                InterviewTurnAnswerStatus.WAITING
        );
        transition(session, InterviewDialogueState.ASK_CORE);
        transition(session, InterviewDialogueState.WAITING_ANSWER);
        session.setCurrentTurn(core);
        session.setAssessmentTurnCount(1);
        session.setDialogueVersion(Math.max(1, value(session.getDialogueVersion(), 1)));
        clearError(session);
        sessionRepository.save(session);
    }

    public ConversationResult confirmTurn(
            UUID sessionId,
            UUID turnId,
            UUID answerClientId,
            Integer expectedDialogueVersion,
            String rawTranscript,
            String finalTranscript
    ) {
        String normalizedFinal = normalize(finalTranscript);
        if (normalizedFinal.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANSWER_REQUIRED",
                    "Câu trả lời không được để trống.");
        }
        AnalysisClaim claim = inTransaction(() -> claimAnswer(
                sessionId,
                turnId,
                answerClientId,
                expectedDialogueVersion,
                rawTranscript,
                normalizedFinal
        ));
        if (claim.idempotent()) {
            return ConversationResult.idempotentResult(claim.advancePending());
        }
        return analyzeAndApply(claim);
    }

    public ConversationResult retryAnalysis(UUID sessionId) {
        AnalysisClaim claim = inTransaction(() -> claimAnalysisRetry(sessionId));
        return analyzeAndApply(claim);
    }

    public ConversationResult skipTurn(
            UUID sessionId,
            UUID turnId,
            UUID answerClientId,
            Integer expectedDialogueVersion
    ) {
        return inTransaction(() -> {
            InterviewSession session = lockOwnedSession(sessionId);
            InterviewConversationTurn duplicate = turnRepository
                    .findBySessionIdAndAnswerClientId(sessionId, answerClientId)
                    .orElse(null);
            if (duplicate != null) {
                if (duplicate.getId().equals(turnId)
                        && duplicate.getAnswerStatus() == InterviewTurnAnswerStatus.SKIPPED) {
                    return ConversationResult.idempotentResult(advancePending(session));
                }
                throw conflict("ANSWER_IDEMPOTENCY_CONFLICT",
                        "Idempotency-Key đã được dùng cho lượt hoặc thao tác khác.");
            }
            requireVersion(session, expectedDialogueVersion);
            InterviewConversationTurn turn = requireCurrentAssessmentTurn(session, turnId);
            requireWaiting(session, turn);

            turn.setAnswerClientId(answerClientId);
            turn.setAnswerStatus(InterviewTurnAnswerStatus.SKIPPED);
            turn.setAnsweredAt(LocalDateTime.now());
            turn.setAnalysisJson(Map.of(
                    "requestedAction", AnswerAnalysisAction.NEXT.name(),
                    "resolvedAction", AnswerAnalysisAction.NEXT.name(),
                    "reason", "SKIPPED"
            ));
            turnRepository.save(turn);
            transition(session, InterviewDialogueState.ANALYZE_ANSWER);
            finalizeAssessmentItem(session, turn.getAssessmentItem());
            appendAcknowledgement(session, turn);
            bumpVersion(session);
            sessionRepository.save(session);
            return new ConversationResult(false, AnswerAnalysisAction.NEXT,
                    turn.getAssessmentItem().getId(), turn.getAssessmentItem().getOrderIndex(), true,
                    turn.getId(), turn.getAnswerClientId(), false);
        });
    }

    public void replayTurn(UUID sessionId, UUID turnId, Integer expectedDialogueVersion) {
        inTransaction(() -> {
            InterviewSession session = lockOwnedSession(sessionId);
            requireVersion(session, expectedDialogueVersion);
            InterviewConversationTurn turn = requireCurrentAssessmentTurn(session, turnId);
            requireWaiting(session, turn);
            turn.setReplayCount(turn.getReplayCount() + 1);
            turnRepository.save(turn);
            int updated = questionRepository.incrementReplayCount(
                    turn.getAssessmentItem().getId(), session.getId());
            if (updated != 1) {
                throw conflict("QUESTION_REPLAY_FAILED",
                        "Không thể ghi nhận lần đọc lại câu hỏi.");
            }
            bumpVersion(session);
            sessionRepository.save(session);
            return null;
        });
    }

    public AdvanceResult advanceAfterCompletedItem(UUID sessionId) {
        return inTransaction(() -> {
            InterviewSession session = lockOwnedSessionAllowCompleted(sessionId);
            if (session.isCompleted()
                    || session.getDialogueState() == InterviewDialogueState.COMPLETED) {
                return AdvanceResult.COMPLETED;
            }
            if (session.getDialogueState() == InterviewDialogueState.WAITING_ANSWER) {
                return AdvanceResult.NEXT_CORE_READY;
            }
            if (session.getDialogueState() == InterviewDialogueState.REVIEW_TRANSCRIPTS) {
                return AdvanceResult.NEEDS_TRANSCRIPT_REVIEW;
            }
            if (session.getDialogueState() == InterviewDialogueState.CLOSING) {
                return AdvanceResult.READY_TO_EVALUATE;
            }
            if (session.getDialogueState() != InterviewDialogueState.ACK_TRANSITION) {
                throw conflict("INTERVIEW_DIALOGUE_NOT_READY",
                        "Luồng hội thoại chưa sẵn sàng để chuyển câu CORE.");
            }

            List<InterviewQuestion> questions = questionRepository
                    .findBySessionIdOrderByOrderIndexAsc(sessionId);
            Set<UUID> completedQuestionIds = answerRepository
                    .findBySessionIdOrderByAnsweredAtAsc(sessionId)
                    .stream()
                    .filter(answer -> answer.getAnsweredAt() != null)
                    .map(InterviewAnswer::getQuestionId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            InterviewQuestion next = questions.stream()
                    .filter(question -> !completedQuestionIds.contains(question.getId()))
                    .findFirst()
                    .orElse(null);
            if (next != null) {
                InterviewConversationTurn previous = session.getCurrentTurn();
                InterviewConversationTurn transitionTurn = newTurn(
                        session,
                        null,
                        previous,
                        InterviewTurnType.TRANSITION,
                        templates.transition(null, next.getOrderIndex()),
                        InterviewTurnAnswerStatus.NOT_REQUIRED
                );
                transition(session, InterviewDialogueState.ASK_CORE);
                InterviewConversationTurn core = newTurn(
                        session,
                        next,
                        transitionTurn,
                        InterviewTurnType.CORE_QUESTION,
                        next.getContent(),
                        InterviewTurnAnswerStatus.WAITING
                );
                transition(session, InterviewDialogueState.WAITING_ANSWER);
                session.setCurrentTurn(core);
                session.setAssessmentTurnCount(value(session.getAssessmentTurnCount(), 0) + 1);
                clearError(session);
                bumpVersion(session);
                sessionRepository.save(session);
                return AdvanceResult.NEXT_CORE_READY;
            }

            if (questions.size() < session.effectiveTargetQuestionCount()) {
                return AdvanceResult.NEEDS_ADAPTIVE_QUESTIONS;
            }
            InterviewConversationTurn closing = newTurn(
                    session,
                    null,
                    session.getCurrentTurn(),
                    InterviewTurnType.CLOSING,
                    templates.closing(),
                    InterviewTurnAnswerStatus.NOT_REQUIRED
            );
            transition(session, InterviewDialogueState.REVIEW_TRANSCRIPTS);
            session.setCurrentTurn(closing);
            clearError(session);
            bumpVersion(session);
            sessionRepository.save(session);
            return AdvanceResult.NEEDS_TRANSCRIPT_REVIEW;
        });
    }

    public void markFailure(
            UUID sessionId,
            String stage,
            String code,
            String message
    ) {
        inTransaction(() -> {
            InterviewSession session = lockOwnedSession(sessionId);
            session.setLastErrorStage(stage);
            session.setLastErrorCode(code);
            session.setLastErrorMessage(message);
            bumpVersion(session);
            sessionRepository.save(session);
            return null;
        });
    }

    private ConversationResult analyzeAndApply(AnalysisClaim claim) {
        ShopAiKeyClient.AnswerDecisionDraft decision;
        if (claim.counters().probeCount() + claim.counters().clarifyCount()
                >= properties.getMaxFollowUpsPerCore()) {
            decision = new ShopAiKeyClient.AnswerDecisionDraft(
                    ShopAiKeyClient.AnswerAnalysisAction.NEXT, null);
        } else {
            try {
                decision = shopAiKeyClient.analyzeAssessmentTurnDecision(
                        claim.session(),
                        claim.question(),
                        claim.currentAnswer(),
                        claim.counters());
            } catch (AiProviderException exception) {
                log.warn("AI assessment decision fallback NEXT applied: "
                                + "sessionId={}, turnId={}, "
                                + "code={}, detail=\"{}\"",
                        claim.session().getId(), claim.turnId(), exception.getCode(), exception.getMessage());
                decision = new ShopAiKeyClient.AnswerDecisionDraft(
                        ShopAiKeyClient.AnswerAnalysisAction.NEXT, null);
            }
        }
        ShopAiKeyClient.AnswerDecisionDraft resolvedDecision = decision;
        return inTransaction(() -> applyDecision(claim, resolvedDecision));
    }

    private AnalysisClaim claimAnswer(
            UUID sessionId,
            UUID turnId,
            UUID answerClientId,
            Integer expectedDialogueVersion,
            String rawTranscript,
            String finalTranscript
    ) {
        InterviewSession session = lockOwnedSession(sessionId);
        String normalizedRaw = normalize(rawTranscript);
        if (normalizedRaw.isBlank()) {
            normalizedRaw = finalTranscript;
        }
        InterviewConversationTurn duplicate = turnRepository
                .findBySessionIdAndAnswerClientId(sessionId, answerClientId)
                .orElse(null);
        if (duplicate != null) {
            if (duplicate.getId().equals(turnId)
                    && duplicate.getAnswerStatus() == InterviewTurnAnswerStatus.CONFIRMED) {
                boolean sameRaw = normalizeForAudit(duplicate.getCandidateRawAnswer())
                        .equals(normalizeForAudit(normalizedRaw));
                boolean sameFinal = normalizeForAudit(duplicate.getCandidateFinalAnswer())
                        .equals(normalizeForAudit(finalTranscript));
                if (!sameRaw || !sameFinal) {
                    throw conflict("IDEMPOTENCY_PAYLOAD_MISMATCH",
                            "Cùng Idempotency-Key nhưng transcript khác lần gửi trước.");
                }
                return AnalysisClaim.idempotentClaim(advancePending(session));
            }
            throw conflict("ANSWER_IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key đã được dùng cho lượt hoặc thao tác khác.");
        }
        requireVersion(session, expectedDialogueVersion);
        InterviewConversationTurn turn = requireCurrentAssessmentTurn(session, turnId);
        requireWaiting(session, turn);
        InterviewAnswer aggregate = answerRepository
                .findBySessionIdAndQuestionId(session.getId(), turn.getAssessmentItem().getId())
                .orElse(null);
        String reviewDraft = hasText(turn.getCandidateFinalAnswer())
                ? turn.getCandidateFinalAnswer()
                : normalizedRaw;
        turn.setCandidateRawAnswer(normalizedRaw);
        turn.setCandidateFinalAnswer(finalTranscript);
        boolean edited = !normalizeForAudit(reviewDraft).equals(normalizeForAudit(finalTranscript));
        turn.setTranscriptEdited(edited);
        if (edited) {
            turn.setEditCount(turn.getEditCount() + 1);
        }
        turn.setAnswerClientId(answerClientId);
        turn.setAnswerStatus(InterviewTurnAnswerStatus.CONFIRMED);
        turn.setAnsweredAt(LocalDateTime.now());
        turnRepository.save(turn);

        transition(session, InterviewDialogueState.ANALYZE_ANSWER);
        clearError(session);
        bumpVersion(session);
        sessionRepository.save(session);
        return analysisClaim(session, turn, session.getDialogueVersion());
    }

    private AnalysisClaim claimAnalysisRetry(UUID sessionId) {
        InterviewSession session = lockOwnedSession(sessionId);
        if (session.getDialogueState() != InterviewDialogueState.ANALYZE_ANSWER
                || !ERROR_STAGE_ANALYSIS.equals(session.getLastErrorStage())) {
            throw conflict("INTERVIEW_RETRY_NOT_AVAILABLE",
                    "Không có bước phân tích câu trả lời nào đang chờ thử lại.");
        }
        InterviewConversationTurn turn = requireCurrentAssessmentTurn(
                session,
                session.getCurrentTurn() == null ? null : session.getCurrentTurn().getId()
        );
        if (turn.getAnswerStatus() != InterviewTurnAnswerStatus.CONFIRMED
                || !hasText(turn.getCandidateFinalAnswer())) {
            throw conflict("INTERVIEW_TURN_NOT_CONFIRMED",
                    "Lượt trả lời chưa được xác nhận để phân tích lại.");
        }
        clearError(session);
        bumpVersion(session);
        sessionRepository.save(session);
        return analysisClaim(session, turn, session.getDialogueVersion());
    }

    private AnalysisClaim analysisClaim(
            InterviewSession session,
            InterviewConversationTurn turn,
            int claimedDialogueVersion
    ) {
        InterviewQuestion question = turn.getAssessmentItem();
        List<InterviewConversationTurn> itemTurns = turnRepository
                .findBySessionIdAndAssessmentItemIdOrderBySequenceNoAsc(
                        session.getId(), question.getId());
        int probes = (int) itemTurns.stream()
                .filter(item -> item.getTurnType() == InterviewTurnType.PROBE)
                .count();
        int clarifies = (int) itemTurns.stream()
                .filter(item -> item.getTurnType() == InterviewTurnType.CLARIFY)
                .count();
        int remainingCore = Math.max(0,
                session.effectiveTargetQuestionCount() - question.getOrderIndex());
        ShopAiKeyClient.AssessmentTurnCounters counters =
                new ShopAiKeyClient.AssessmentTurnCounters(
                        probes,
                        clarifies,
                        value(session.getAssessmentTurnCount(), 0),
                        remainingCore
                );
        return new AnalysisClaim(
                false,
                false,
                session,
                question,
                turn.getId(),
                turn.getCandidateFinalAnswer(),
                counters,
                claimedDialogueVersion
        );
    }

    private ConversationResult applyDecision(
            AnalysisClaim claim,
            ShopAiKeyClient.AnswerDecisionDraft decision
    ) {
        InterviewSession session = lockOwnedSession(claim.session().getId());
        if (session.getDialogueState() != InterviewDialogueState.ANALYZE_ANSWER
                || session.getCurrentTurn() == null
                || !session.getCurrentTurn().getId().equals(claim.turnId())
                || value(session.getDialogueVersion(), 0) != claim.claimedDialogueVersion()) {
            throw conflict("INTERVIEW_STATE_CHANGED",
                    "Trạng thái phỏng vấn đã thay đổi trong khi AI đang phân tích.");
        }
        InterviewConversationTurn turn = turnRepository
                .findByIdAndSessionId(claim.turnId(), session.getId())
                .orElseThrow(() -> conflict("INTERVIEW_TURN_NOT_FOUND",
                        "Không tìm thấy lượt hội thoại đang phân tích."));
        InterviewQuestion question = turn.getAssessmentItem();
        AnswerAnalysisAction resolved = policy.resolveAction(
                decision.action().name(),
                claim.counters().probeCount(),
                claim.counters().clarifyCount(),
                claim.counters().totalAssessmentTurns(),
                claim.counters().remainingCoreQuestions(),
                session.effectiveTargetQuestionCount()
        );
        turn.setAnalysisJson(decisionMap(decision, resolved));
        turnRepository.save(turn);

        if (resolved == AnswerAnalysisAction.PROBE || resolved == AnswerAnalysisAction.CLARIFY) {
            InterviewTurnType followUpType = resolved == AnswerAnalysisAction.PROBE
                    ? InterviewTurnType.PROBE
                    : InterviewTurnType.CLARIFY;
            InterviewDialogueState askState = resolved == AnswerAnalysisAction.PROBE
                    ? InterviewDialogueState.ASK_PROBE
                    : InterviewDialogueState.ASK_CLARIFY;
            transition(session, askState);
            InterviewConversationTurn followUp = newTurn(
                    session,
                    question,
                    turn,
                    followUpType,
                    decision.followUp(),
                    InterviewTurnAnswerStatus.WAITING
            );
            transition(session, InterviewDialogueState.WAITING_ANSWER);
            session.setCurrentTurn(followUp);
            session.setAssessmentTurnCount(value(session.getAssessmentTurnCount(), 0) + 1);
            clearError(session);
            bumpVersion(session);
            sessionRepository.save(session);
            return new ConversationResult(false, resolved, question.getId(),
                    question.getOrderIndex(), false, turn.getId(), turn.getAnswerClientId(),
                    requiresAdaptiveEvidence(turn, question));
        }

        finalizeAssessmentItem(session, question);
        appendAcknowledgement(session, turn);
        clearError(session);
        bumpVersion(session);
        sessionRepository.save(session);
        return new ConversationResult(false, AnswerAnalysisAction.NEXT, question.getId(),
                question.getOrderIndex(), true, turn.getId(), turn.getAnswerClientId(),
                requiresAdaptiveEvidence(turn, question));
    }

    private boolean requiresAdaptiveEvidence(
            InterviewConversationTurn turn,
            InterviewQuestion question
    ) {
        if (question.getOrderIndex() <= 2) return true;
        return question.getOrderIndex() == 3
                && turn.getTurnType() == InterviewTurnType.CORE_QUESTION;
    }

    private void finalizeAssessmentItem(InterviewSession session, InterviewQuestion question) {
        List<InterviewConversationTurn> evidenceTurns = turnRepository
                .findBySessionIdAndAssessmentItemIdOrderBySequenceNoAsc(session.getId(), question.getId());
        List<InterviewConversationTurn> confirmed = evidenceTurns.stream()
                .filter(turn -> turn.getAnswerStatus() == InterviewTurnAnswerStatus.CONFIRMED)
                .filter(turn -> hasText(turn.getCandidateFinalAnswer()))
                .toList();
        InterviewAnswer aggregate = answerRepository
                .findBySessionIdAndQuestionId(session.getId(), question.getId())
                .orElseGet(() -> draftAnswer(session, question));
        if (confirmed.isEmpty()) {
            aggregate.setRawTranscript("[SKIPPED]");
            aggregate.setFinalTranscript("[SKIPPED]");
            aggregate.setTranscriptText("[SKIPPED]");
            aggregate.setSkipped(true);
            aggregate.setFeedbackStatus("completed");
            aggregate.setEvaluationSource("skipped");
        } else {
            aggregate.setRawTranscript(joinAnswers(confirmed, true));
            aggregate.setFinalTranscript(joinAnswers(confirmed, false));
            aggregate.setTranscriptText(aggregate.getFinalTranscript());
            aggregate.setSkipped(false);
            aggregate.setFeedbackStatus("pending");
            aggregate.setEvaluationSource("provider");
        }
        aggregate.setTranscriptEdited(confirmed.stream()
                .anyMatch(InterviewConversationTurn::isTranscriptEdited));
        aggregate.setTranscriptEditCount(confirmed.stream()
                .mapToInt(InterviewConversationTurn::getEditCount)
                .sum());
        aggregate.setConversationState("ANSWER_CONFIRMED");
        aggregate.setTranscriptStatus("completed");
        aggregate.setAnsweredAt(LocalDateTime.now());
        aggregate.setErrorMessage(null);
        aggregate.setEvaluationFallback(false);
        answerRepository.saveAndFlush(aggregate);
    }

    private void appendAcknowledgement(
            InterviewSession session,
            InterviewConversationTurn answeredTurn
    ) {
        transition(session, InterviewDialogueState.ACK_TRANSITION);
        InterviewConversationTurn acknowledgement = newTurn(
                session,
                null,
                answeredTurn,
                InterviewTurnType.ACKNOWLEDGEMENT,
                templates.acknowledgement(answeredTurn.getSequenceNo()),
                InterviewTurnAnswerStatus.NOT_REQUIRED
        );
        session.setCurrentTurn(acknowledgement);
    }

    private void markAnalysisFailure(AnalysisClaim claim, AiProviderException exception) {
        inTransaction(() -> {
            CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
            InterviewSession session = sessionRepository
                    .findOwnedForUpdate(claim.session().getId(), candidate.getId())
                    .orElse(null);
            if (session == null
                    || session.getDialogueState() != InterviewDialogueState.ANALYZE_ANSWER
                    || session.getCurrentTurn() == null
                    || !session.getCurrentTurn().getId().equals(claim.turnId())
                    || value(session.getDialogueVersion(), 0) != claim.claimedDialogueVersion()) {
                return null;
            }
            session.setLastErrorStage(ERROR_STAGE_ANALYSIS);
            session.setLastErrorCode(exception.getCode());
            session.setLastErrorMessage(exception.getMessage());
            bumpVersion(session);
            sessionRepository.save(session);
            return null;
        });
    }

    private InterviewConversationTurn requireCurrentAssessmentTurn(
            InterviewSession session,
            UUID turnId
    ) {
        if (turnId == null || session.getCurrentTurn() == null
                || !turnId.equals(session.getCurrentTurn().getId())) {
            throw conflict("INTERVIEW_TURN_NOT_CURRENT",
                    "Chỉ có thể thao tác với lượt hội thoại hiện tại.");
        }
        InterviewConversationTurn turn = turnRepository
                .findByIdAndSessionId(turnId, session.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "INTERVIEW_TURN_NOT_FOUND", "Không tìm thấy lượt hội thoại."));
        if (!policy.isAssessmentTurn(turn.getTurnType()) || turn.getAssessmentItem() == null) {
            throw conflict("INTERVIEW_TURN_NOT_ANSWERABLE",
                    "Lượt hội thoại này không nhận câu trả lời.");
        }
        return turn;
    }

    private void requireWaiting(
            InterviewSession session,
            InterviewConversationTurn turn
    ) {
        if (session.getDialogueState() != InterviewDialogueState.WAITING_ANSWER
                || (turn.getAnswerStatus() != InterviewTurnAnswerStatus.WAITING
                && turn.getAnswerStatus() != InterviewTurnAnswerStatus.REVIEWING)) {
            throw conflict("INTERVIEW_TURN_NOT_WAITING",
                    "Lượt hội thoại không ở trạng thái chờ xác nhận.");
        }
    }

    private InterviewSession lockOwnedSession(UUID sessionId) {
        InterviewSession session = lockOwnedSessionAllowCompleted(sessionId);
        if (session.isCompleted()) {
            throw conflict("SESSION_COMPLETED", "Phiên phỏng vấn đã hoàn thành.");
        }
        return session;
    }

    private InterviewSession lockOwnedSessionAllowCompleted(UUID sessionId) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        return sessionRepository
                .findOwnedForUpdate(sessionId, candidate.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "SESSION_NOT_FOUND", "Không tìm thấy phiên phỏng vấn."));
    }

    private boolean advancePending(InterviewSession session) {
        return session.getDialogueState() == InterviewDialogueState.ACK_TRANSITION
                || session.getDialogueState() == InterviewDialogueState.CLOSING;
    }

    private void requireVersion(InterviewSession session, Integer expectedDialogueVersion) {
        if (expectedDialogueVersion != null
                && expectedDialogueVersion != value(session.getDialogueVersion(), 1)) {
            throw conflict("INTERVIEW_STATE_VERSION_CONFLICT",
                    "Trạng thái phỏng vấn đã thay đổi. Hãy tải snapshot mới nhất.");
        }
    }

    private InterviewConversationTurn newTurn(
            InterviewSession session,
            InterviewQuestion assessmentItem,
            InterviewConversationTurn replyTo,
            InterviewTurnType type,
            String text,
            InterviewTurnAnswerStatus answerStatus
    ) {
        InterviewConversationTurn turn = new InterviewConversationTurn();
        turn.setSession(session);
        turn.setAssessmentItem(assessmentItem);
        turn.setReplyToTurn(replyTo);
        turn.setSequenceNo(value(session.getNextTurnSequence(), 1));
        session.setNextTurnSequence(turn.getSequenceNo() + 1);
        turn.setTurnType(type);
        turn.setText(normalize(text));
        turn.setAnswerStatus(answerStatus);
        return turnRepository.save(turn);
    }

    private InterviewAnswer draftAnswer(InterviewSession session, InterviewQuestion question) {
        InterviewAnswer answer = new InterviewAnswer();
        answer.setSession(session);
        answer.setQuestionId(question.getId());
        answer.setTranscriptStatus("pending");
        answer.setFeedbackStatus("pending");
        answer.setConversationState("LISTENING");
        return answerRepository.save(answer);
    }

    private Map<String, Object> decisionMap(
            ShopAiKeyClient.AnswerDecisionDraft decision,
            AnswerAnalysisAction resolved
    ) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("requestedAction", decision.action().name());
        value.put("resolvedAction", resolved.name());
        value.put("followUp", resolved == AnswerAnalysisAction.NEXT ? null : decision.followUp());
        value.put("evidenceStatus", "PENDING");
        return value;
    }

    private String joinAnswers(List<InterviewConversationTurn> turns, boolean raw) {
        return turns.stream()
                .map(turn -> raw ? turn.getCandidateRawAnswer() : turn.getCandidateFinalAnswer())
                .filter(this::hasText)
                .map(this::normalize)
                .collect(Collectors.joining("\n"));
    }

    private String targetRole(InterviewSession session) {
        if (session.getEvaluationProfile() != null) {
            Object role = session.getEvaluationProfile().get("targetRole");
            if (role != null && hasText(String.valueOf(role))) {
                return String.valueOf(role);
            }
        }
        return session.getJob() == null ? session.getTitle() : session.getJob().getTitle();
    }

    private void transition(InterviewSession session, InterviewDialogueState next) {
        InterviewDialogueState current = session.getDialogueState();
        policy.requireTransition(current, next);
        session.setDialogueState(next);
    }

    private void bumpVersion(InterviewSession session) {
        session.setDialogueVersion(value(session.getDialogueVersion(), 1) + 1);
    }

    private void clearError(InterviewSession session) {
        session.setLastErrorStage(null);
        session.setLastErrorCode(null);
        session.setLastErrorMessage(null);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeForAudit(String value) {
        return normalize(value).toLowerCase(java.util.Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public enum AdvanceResult {
        NEXT_CORE_READY,
        NEEDS_ADAPTIVE_QUESTIONS,
        NEEDS_TRANSCRIPT_REVIEW,
        READY_TO_EVALUATE,
        COMPLETED
    }

    public record ConversationResult(
            boolean idempotent,
            AnswerAnalysisAction action,
            UUID assessmentItemId,
            int assessmentItemOrder,
            boolean itemCompleted,
            UUID confirmedTurnId,
            UUID answerClientId,
            boolean evidenceEnrichmentRequired
    ) {
        static ConversationResult idempotentResult(boolean advancePending) {
            return new ConversationResult(
                    true, null, null, 0, advancePending, null, null, false);
        }
    }

    private record AnalysisClaim(
            boolean idempotent,
            boolean advancePending,
            InterviewSession session,
            InterviewQuestion question,
            UUID turnId,
            String currentAnswer,
            ShopAiKeyClient.AssessmentTurnCounters counters,
            int claimedDialogueVersion
    ) {
        static AnalysisClaim idempotentClaim(boolean advancePending) {
            return new AnalysisClaim(
                    true, advancePending, null, null, null, null, null, 0);
        }
    }
}
