package com.sjp.recruitment.service;

import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewConversationTurn;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.enums.InterviewTurnAnswerStatus;
import com.sjp.recruitment.repository.InterviewAnswerRepository;
import com.sjp.recruitment.repository.InterviewConversationTurnRepository;
import com.sjp.recruitment.repository.InterviewSessionRepository;
import com.sjp.recruitment.service.ai.AiProviderException;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiInterviewTurnEvidenceService {

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_COMPLETED = "COMPLETED";
    static final String STATUS_FAILED = "FAILED";

    private final InterviewSessionRepository sessionRepository;
    private final InterviewConversationTurnRepository turnRepository;
    private final InterviewAnswerRepository answerRepository;
    private final ShopAiKeyClient shopAiKeyClient;
    private final TransactionTemplate transactions;

    public void enrich(EvidenceJob job) {
        try {
            EvidenceContext context = inTransaction(() -> loadContext(job));
            if (context == null) return;
            ShopAiKeyClient.AnswerEvidenceDraft evidence =
                    shopAiKeyClient.analyzeAssessmentTurnEvidence(
                            context.session(),
                            context.question(),
                            context.currentAnswer(),
                            context.itemEvidenceSummary());
            persistWithRetry(job, evidence);
        } catch (AiProviderException exception) {
            markFailed(job, exception.getCode(), exception.getMessage());
            log.warn("AI turn evidence enrichment failed: sessionId={}, turnId={}, code={}, detail=\"{}\"",
                    job.sessionId(), job.turnId(), exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            markFailed(job, "EVIDENCE_BACKGROUND_FAILED", exception.getMessage());
            log.warn("AI turn evidence enrichment failed: sessionId={}, turnId={}, code={}, detail=\"{}\"",
                    job.sessionId(), job.turnId(), exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    public void markFailed(EvidenceJob job, String code, String message) {
        try {
            inTransaction(() -> {
                InterviewConversationTurn turn = matchingTurn(job);
                if (turn == null || STATUS_COMPLETED.equals(evidenceStatus(turn))) return null;
                Map<String, Object> analysis = mutableAnalysis(turn);
                analysis.put("evidenceStatus", STATUS_FAILED);
                analysis.put("evidenceErrorCode", normalize(code));
                analysis.put("evidenceErrorMessage", normalize(message));
                turn.setAnalysisJson(analysis);
                turnRepository.save(turn);
                return null;
            });
        } catch (RuntimeException persistenceFailure) {
            log.warn("Could not persist AI turn evidence failure: sessionId={}, turnId={}, code={}",
                    job.sessionId(), job.turnId(), persistenceFailure.getClass().getSimpleName());
        }
    }

    private EvidenceContext loadContext(EvidenceJob job) {
        InterviewConversationTurn turn = matchingTurn(job);
        if (turn == null || STATUS_COMPLETED.equals(evidenceStatus(turn))) return null;
        if (turn.getAnswerStatus() != InterviewTurnAnswerStatus.CONFIRMED
                || !hasText(turn.getCandidateFinalAnswer())
                || turn.getAssessmentItem() == null) {
            throw new AiProviderException(
                    "EVIDENCE_CONTEXT_INVALID",
                    "Lượt hội thoại chưa có câu trả lời đã xác nhận để phân tích evidence");
        }
        InterviewSession session = sessionRepository.findById(job.sessionId()).orElse(null);
        if (session == null) return null;
        InterviewQuestion question = turn.getAssessmentItem();
        Map<String, Object> summary = answerRepository
                .findBySessionIdAndQuestionId(job.sessionId(), question.getId())
                .map(InterviewAnswer::getEvidenceSummaryJson)
                .<Map<String, Object>>map(LinkedHashMap::new)
                .orElseGet(Map::of);
        return new EvidenceContext(
                sessionSnapshot(session),
                questionSnapshot(question),
                turn.getCandidateFinalAnswer(),
                Collections.unmodifiableMap(new LinkedHashMap<>(summary)));
    }

    private InterviewSession sessionSnapshot(InterviewSession source) {
        InterviewSession snapshot = new InterviewSession();
        snapshot.setId(source.getId());
        snapshot.setEvaluationProfile(source.getEvaluationProfile() == null
                ? Map.of()
                : new LinkedHashMap<>(source.getEvaluationProfile()));
        return snapshot;
    }

    private InterviewQuestion questionSnapshot(InterviewQuestion source) {
        InterviewQuestion snapshot = new InterviewQuestion();
        snapshot.setId(source.getId());
        snapshot.setQuestionType(source.getQuestionType());
        snapshot.setCompetencyId(source.getCompetencyId());
        snapshot.setContent(source.getContent());
        snapshot.setRubric(source.getRubric() == null
                ? Map.of()
                : new LinkedHashMap<>(source.getRubric()));
        return snapshot;
    }

    private void persistWithRetry(
            EvidenceJob job,
            ShopAiKeyClient.AnswerEvidenceDraft evidence
    ) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                inTransaction(() -> {
                    persist(job, evidence);
                    return null;
                });
                return;
            } catch (OptimisticLockingFailureException exception) {
                if (attempt == 2) throw exception;
                log.debug("Retrying AI turn evidence persistence after optimistic conflict: "
                                + "sessionId={}, turnId={}",
                        job.sessionId(), job.turnId());
            }
        }
    }

    private void persist(EvidenceJob job, ShopAiKeyClient.AnswerEvidenceDraft evidence) {
        InterviewConversationTurn turn = matchingTurn(job);
        if (turn == null || STATUS_COMPLETED.equals(evidenceStatus(turn))) return;
        InterviewQuestion question = turn.getAssessmentItem();
        InterviewSession session = sessionRepository.findById(job.sessionId()).orElse(null);
        if (question == null || session == null) return;

        Map<String, Object> analysis = mutableAnalysis(turn);
        analysis.put("keyClaims", evidence.keyClaims());
        analysis.put("evidenceCoverage", evidence.evidenceCoverage());
        analysis.put("missingEvidence", evidence.missingEvidence());
        analysis.put("updatedItemSummary", evidence.updatedItemSummary());
        analysis.put("globalEvidenceDelta", globalEvidenceDeltaMap(evidence.globalEvidenceDelta()));
        analysis.put("evidenceStatus", STATUS_COMPLETED);
        analysis.remove("evidenceErrorCode");
        analysis.remove("evidenceErrorMessage");
        turn.setAnalysisJson(analysis);
        turnRepository.save(turn);

        InterviewAnswer aggregate = answerRepository
                .findBySessionIdAndQuestionId(job.sessionId(), question.getId())
                .orElseGet(() -> draftAnswer(session, question));
        aggregate.setEvidenceSummaryJson(itemEvidenceSummary(evidence));
        answerRepository.save(aggregate);

        session.setEvidenceSummaryJson(mergeGlobalEvidence(
                session.getEvidenceSummaryJson(), question.getCompetencyId(), evidence));
        sessionRepository.save(session);
    }

    private InterviewConversationTurn matchingTurn(EvidenceJob job) {
        return turnRepository.findByIdAndSessionIdForUpdate(job.turnId(), job.sessionId())
                .filter(turn -> Objects.equals(turn.getAnswerClientId(), job.answerClientId()))
                .orElse(null);
    }

    private String evidenceStatus(InterviewConversationTurn turn) {
        Object value = turn.getAnalysisJson() == null
                ? null
                : turn.getAnalysisJson().get("evidenceStatus");
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> mutableAnalysis(InterviewConversationTurn turn) {
        return turn.getAnalysisJson() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(turn.getAnalysisJson());
    }

    private InterviewAnswer draftAnswer(InterviewSession session, InterviewQuestion question) {
        InterviewAnswer answer = new InterviewAnswer();
        answer.setSession(session);
        answer.setQuestionId(question.getId());
        answer.setTranscriptStatus("pending");
        answer.setFeedbackStatus("pending");
        answer.setConversationState("LISTENING");
        return answer;
    }

    private Map<String, Object> itemEvidenceSummary(ShopAiKeyClient.AnswerEvidenceDraft evidence) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("summary", evidence.updatedItemSummary());
        value.put("keyClaims", evidence.keyClaims());
        value.put("evidenceCoverage", evidence.evidenceCoverage());
        value.put("missingEvidence", evidence.missingEvidence());
        return value;
    }

    private Map<String, Object> mergeGlobalEvidence(
            Map<String, Object> current,
            String competencyId,
            ShopAiKeyClient.AnswerEvidenceDraft evidence
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (current != null) merged.putAll(current);
        ShopAiKeyClient.GlobalEvidenceDeltaDraft delta = evidence.globalEvidenceDelta();
        merged.put("demonstratedCompetencies", mergeList(
                merged.get("demonstratedCompetencies"), delta.demonstratedCompetencyIds(), 12));
        merged.put("weakEvidence", mergeList(
                merged.get("weakEvidence"), delta.weakEvidence(), 20));
        merged.put("interestingClaims", mergeList(
                merged.get("interestingClaims"), delta.interestingClaims(), 20));
        merged.put("unverifiedClaims", mergeList(
                merged.get("unverifiedClaims"), delta.unverifiedClaims(), 20));
        List<String> insufficient = evidence.missingEvidence().isEmpty()
                ? List.of()
                : List.of(competencyId);
        merged.put("insufficientlyCoveredCompetencies", mergeList(
                merged.get("insufficientlyCoveredCompetencies"), insufficient, 12));
        return merged;
    }

    private Map<String, Object> globalEvidenceDeltaMap(
            ShopAiKeyClient.GlobalEvidenceDeltaDraft delta
    ) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("demonstratedCompetencyIds", delta.demonstratedCompetencyIds());
        value.put("weakEvidence", delta.weakEvidence());
        value.put("interestingClaims", delta.interestingClaims());
        value.put("unverifiedClaims", delta.unverifiedClaims());
        return value;
    }

    private List<String> mergeList(Object current, Collection<String> additions, int limit) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (current instanceof Collection<?> existing) {
            existing.stream().map(String::valueOf).map(this::normalize)
                    .filter(this::hasText).forEach(values::add);
        }
        if (additions != null) {
            additions.stream().filter(Objects::nonNull).map(this::normalize)
                    .filter(this::hasText).forEach(values::add);
        }
        return values.stream().limit(limit).toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    public record EvidenceJob(UUID sessionId, UUID turnId, UUID answerClientId) {
        public EvidenceJob {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(turnId, "turnId");
            Objects.requireNonNull(answerClientId, "answerClientId");
        }
    }

    private record EvidenceContext(
            InterviewSession session,
            InterviewQuestion question,
            String currentAnswer,
            Map<String, Object> itemEvidenceSummary
    ) {
    }
}
