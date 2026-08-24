package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.AiInterviewPracticeSessionRequest;
import com.sjp.recruitment.model.dto.request.AiInterviewTranscriptReviewRequest;
import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.model.entity.*;
import com.sjp.recruitment.repository.*;
import com.sjp.recruitment.service.ai.AiProviderException;
import com.sjp.recruitment.service.ai.GladiaTranscriptionClient;
import com.sjp.recruitment.service.ai.AiInterviewRateLimiter;
import com.sjp.recruitment.service.ai.AiInterviewSpeechPrefetchService;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import com.sjp.recruitment.model.enums.InterviewDialogueState;
import com.sjp.recruitment.model.enums.InterviewTurnAnswerStatus;
import com.sjp.recruitment.model.enums.InterviewTurnType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiInterviewService {

    private static final String PROVIDER_RETRY_MESSAGE = "Hệ thống chưa xử lý được câu trả lời này, vui lòng thử lại.";
    private static final String AI_QUESTION_SOURCE = "AI_GENERATED";
    private static final String QUESTION_BANK_SOURCE = "QUESTION_BANK";
    private static final String INITIAL_QUESTION_PROMPT_VERSION = "ai-question-initial-v2";
    private static final String ADAPTIVE_QUESTION_PROMPT_VERSION = "ai-question-adaptive-v3";
    private static final String EVALUATION_PROFILE_VERSION = "evaluation-profile-v2";
    private static final String BARS_RUBRIC_VERSION = "bars-v2";

    private final AiInterviewProperties properties;
    private final CandidateService candidateService;
    private final AiInterviewCvProfileService aiInterviewCvProfileService;
    private final AiInterviewScoreCalculator scoreCalculator;
    private final GladiaVoiceEvidenceService voiceEvidenceService;
    private final ApplicationRepository applicationRepository;
    private final CandidateCvRepository candidateCvRepository;
    private final JobRepository jobRepository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final AiAnswerFeedbackRepository answerFeedbackRepository;
    private final AiSessionFeedbackRepository sessionFeedbackRepository;
    private final AiQuestionSetRepository questionSetRepository;
    private final AiQuestionBankRepository questionBankRepository;
    private final ShopAiKeyClient shopAiKeyClient;
    private final GladiaTranscriptionClient gladiaTranscriptionClient;
    private final JobService jobService;
    private final AiInterviewRateLimiter rateLimiter;
    private final AiInterviewSpeechPrefetchService speechPrefetchService;
    private final AiInterviewResponseAssembler responseAssembler;
    private final AiInterviewConversationService conversationService;
    private final AiInterviewConversationTemplateBank conversationTemplates;
    private final AiInterviewAdaptivePrefetchCoordinator adaptivePrefetchCoordinator;
    private final AiInterviewTurnEvidenceCoordinator turnEvidenceCoordinator;
    private final AiInterviewTranscriptCorrectionCoordinator transcriptCorrectionCoordinator;
    private final AiInterviewTranscriptReviewService transcriptReviewService;
    private final AiInterviewFallbackFactory fallbackFactory;
    private final InterviewConversationTurnRepository conversationTurnRepository;
    private final FeatureLimitService featureLimitService;
    private final SystemSettingsService systemSettingsService;
    private final TransactionTemplate transactions;

    @Transactional(readOnly = true)
    public AiInterviewConfigResponse configStatus() {
        boolean configured = properties.isEnabled();
        boolean enabledByAdmin = isEnabledByAdmin();
        boolean enabled = configured && enabledByAdmin;
        String message = null;
        if (!configured) {
            if (!properties.isCoreConfigured()) {
                message = "AI Interview chưa được cấu hình API key.";
            } else if (!properties.isVoiceConfigured()) {
                message = "Chưa cấu hình đầy đủ dịch vụ giọng đọc phỏng vấn.";
            } else {
                message = "Chưa cấu hình đầy đủ dịch vụ nhận dạng giọng nói realtime.";
            }
        } else if (!enabledByAdmin) {
            message = "Phỏng vấn AI đang bị tắt bởi quản trị viên.";
        }
        return new AiInterviewConfigResponse(
                enabled,
                message,
                properties.effectiveCoreQuestionCount(),
                properties.getAudioMaxSeconds(),
                properties.getAudioMaxSizeMb(),
                properties.getAnswerTranscriptionProvider(),
                properties.isSpeechmaticsRealtimeEnabled() && properties.isAnswerTranscriptionConfigured(),
                properties.isVoiceStreamingEnabled(),
                properties.getVoiceProvider(),
                properties.getVoiceConfirmationPromptDelayMs(),
                properties.getVoiceConfirmationAutoFinalizeMs(),
                properties.getVoiceRecognitionRestartDelayMs(),
                properties.getVoiceLoadWaitMs(),
                properties.getVoiceNextQuestionDelayMs()
        );
    }

    @Transactional(readOnly = true)
    public List<AiInterviewEligibleApplicationResponse> eligibleApplications() {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        return applicationRepository.findByCandidateId(candidate.getId(), Pageable.unpaged())
                .getContent()
                .stream()
                .filter(this::isEligibleApplication)
                .map(application -> new AiInterviewEligibleApplicationResponse(
                        application.getId().toString(),
                        application.getStatusEnum().name(),
                        application.getSubmittedAt() == null ? null : application.getSubmittedAt().toString(),
                        jobService.toJobResponse(application.getJob(), candidate)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiInterviewQuestionSetResponse> questionSets() {
        ensureEnabled();
        return questionSetRepository.findByActiveTrueOrderByTitleAsc()
                .stream()
                .map(questionSet -> new AbstractMap.SimpleEntry<>(
                        questionSet,
                        questionBankRepository.countByQuestionSet_IdAndActiveTrue(questionSet.getId())
                ))
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new AiInterviewQuestionSetResponse(
                        entry.getKey().getId().toString(),
                        entry.getKey().getCode(),
                        entry.getKey().getTitle(),
                        entry.getKey().getDescription(),
                        entry.getKey().getTargetRole(),
                        entry.getValue()
                ))
                .toList();
    }

    @Transactional
    public AiInterviewSessionResponse createApplicationSession(String applicationId) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "session-create");
        requireAiSession(candidate.getUser());
        Application application = applicationRepository.findById(parseUuid(applicationId, "APPLICATION_ID_INVALID"))
                .filter(item -> item.getCandidate().getId().equals(candidate.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Không tìm thấy hồ sơ ứng tuyển"));
        if (!isEligibleApplication(application)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "APPLICATION_NOT_ELIGIBLE", "Hồ sơ ứng tuyển này không còn đủ điều kiện luyện phỏng vấn AI");
        }

        InterviewSession session = new InterviewSession();
        session.setCandidate(candidate);
        session.setApplication(application);
        session.setJob(application.getJob());
        session.setContextType("application");
        session.setSessionType("job_based");
        session.setTitle("Luyện phỏng vấn: " + application.getJob().getTitle());
        session.setStatus("created");
        session.setStartedAt(LocalDateTime.now());
        session = sessionRepository.save(session);
        createNextQuestion(session);
        conversationService.initialize(session);
        session.setStatus("in_progress");
        session = sessionRepository.save(session);
        prefetchInitialConversationSpeech(session);
        consumeAiSession(candidate.getUser());
        return responseAssembler.assemble(session);
    }

    @Transactional
    public AiInterviewSessionResponse createPracticeSession(AiInterviewPracticeSessionRequest request) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "session-create");
        requireAiSession(candidate.getUser());
        AiInterviewCvProfileResponse cvProfile = aiInterviewCvProfileService.analyze(request.cvId());
        List<String> focusSkills = request.focusSkills() == null
                ? List.of()
                : request.focusSkills().stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(3)
                .toList();

        Map<String, Object> practiceContext = new LinkedHashMap<>();
        practiceContext.put("cvId", cvProfile.cvId());
        practiceContext.put("cvProfileId", cvProfile.id());
        practiceContext.put("cvContentHash", cvProfile.contentHash());
        practiceContext.put("cvSummary", cvProfile.summary());
        practiceContext.put("cvSkills", cvProfile.skills());
        practiceContext.put("evidenceClaims", cvProfile.evidenceClaims());
        practiceContext.put("targetRole", request.targetRole().trim());
        practiceContext.put("seniority", request.seniority());
        practiceContext.put("focusSkills", focusSkills);
        practiceContext.put("questionMode", "ai_generated");

        InterviewSession session = new InterviewSession();
        session.setCandidate(candidate);
        session.setJob(null);
        session.setContextType("practice");
        session.setSessionType("practice");
        session.setPracticeContext(practiceContext);
        session.setTitle("Luyện tập: " + request.targetRole().trim());
        session.setStatus("created");
        session.setStartedAt(LocalDateTime.now());
        session = sessionRepository.save(session);
        createNextQuestion(session);
        conversationService.initialize(session);
        session.setStatus("in_progress");
        session = sessionRepository.save(session);
        prefetchInitialConversationSpeech(session);
        consumeAiSession(candidate.getUser());
        return responseAssembler.assemble(session);
    }

    @Transactional(readOnly = true)
    public List<AiInterviewSessionResponse> sessions(int page, int size) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        return responseAssembler.assembleAll(sessionRepository.findByCandidateIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
                candidate.getId(),
                org.springframework.data.domain.PageRequest.of(safePage, safeSize)
        ));
    }

    @Transactional(readOnly = true)
    public AiInterviewSessionResponse session(String sessionId) {
        ensureEnabled();
        return responseAssembler.assemble(requireSession(sessionId));
    }

    @Transactional
    public void deleteSession(String sessionId) {
        ensureEnabled();
        InterviewSession session = requireSession(sessionId);
        session.setDeletedAt(LocalDateTime.now());
    }

    @Transactional
    public AiInterviewTranscriptResponse transcribeCurrentQuestion(String sessionId, MultipartFile file, Integer durationSeconds) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "transcribe");
        InterviewSession session = requireMutableSession(sessionId);
        validateAudio(file, durationSeconds);
        InterviewQuestion question = currentQuestion(session);
        InterviewAnswer answer = answerRepository.findBySessionIdAndQuestionId(session.getId(), question.getId())
                .orElseGet(() -> draftAnswer(session, question));
        if (answer.getAnsweredAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "QUESTION_ALREADY_ANSWERED", "Câu hỏi này đã được chốt câu trả lời");
        }
        answer.setTranscriptStatus("processing");
        answer.setErrorMessage(null);
        answer.setDurationSeconds(durationSeconds);
        saveAnswerClaim(answer);
        try {
            String transcript = gladiaTranscriptionClient.transcribe(file);
            answer.setTranscriptText(transcript);
            answer.setRawTranscript(transcript);
            answer.setConversationState("REVIEWING_TRANSCRIPT");
            answer.setTranscriptStatus("completed");
            answer.setFeedbackStatus("pending");
            answerRepository.save(answer);
            return new AiInterviewTranscriptResponse(question.getId().toString(), transcript, "completed");
        } catch (AiProviderException exception) {
            answer.setTranscriptStatus("failed");
            answer.setConversationState(hasText(answer.getRawTranscript())
                    ? "REVIEWING_TRANSCRIPT" : "LISTENING");
            answer.setErrorMessage(PROVIDER_RETRY_MESSAGE);
            answerRepository.save(answer);
            throw new ApiException(HttpStatus.BAD_GATEWAY, exception.getCode(), PROVIDER_RETRY_MESSAGE);
        }
    }

    public AiInterviewSessionResponse submitAnswer(String sessionId, String questionId, String transcript) {
        return confirmAnswer(sessionId, questionId, transcript);
    }

    public AiInterviewSessionResponse replayQuestion(String sessionId, String questionId) {
        ensureEnabled();
        candidateService.getCurrentCandidateProfile();
        UUID parsedQuestionId = parseUuid(questionId, "QUESTION_ID_INVALID");
        inTransaction(() -> {
            InterviewSession session = requireMutableSession(sessionId);
            requireLegacyQuestionFlow(session);
            InterviewQuestion question = requireQuestion(session, questionId);
            InterviewQuestion openQuestion = currentQuestion(session);
            if (!openQuestion.getId().equals(question.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "QUESTION_NOT_CURRENT",
                        "Chỉ có thể đọc lại câu hỏi hiện tại");
            }
            if (answerRepository.findBySessionIdAndQuestionId(session.getId(), question.getId())
                    .map(answer -> answer.getAnsweredAt() != null)
                    .orElse(false)) {
                throw new ApiException(HttpStatus.CONFLICT, "QUESTION_ALREADY_ANSWERED",
                        "Câu hỏi này đã được chốt câu trả lời");
            }
            int updated = questionRepository.incrementReplayCount(parsedQuestionId, session.getId());
            if (updated != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "QUESTION_REPLAY_FAILED",
                        "Không thể ghi nhận lần đọc lại câu hỏi");
            }
            return null;
        });
        return responseAssembler.assemble(requireSession(sessionId));
    }

    public AiInterviewSessionResponse confirmAnswer(String sessionId, String questionId, String transcript) {
        return confirmAnswer(sessionId, questionId, null, transcript);
    }

    public AiInterviewSessionResponse confirmConversationTurn(
            String sessionId,
            String turnId,
            String idempotencyKey,
            String rawTranscript,
            String finalTranscript,
            Integer expectedDialogueVersion
    ) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "answer");
        UUID parsedSessionId = parseUuid(sessionId, "SESSION_ID_INVALID");
        AiInterviewConversationService.ConversationResult result;
        try {
            result = conversationService.confirmTurn(
                    parsedSessionId,
                    parseUuid(turnId, "TURN_ID_INVALID"),
                    parseUuid(idempotencyKey, "IDEMPOTENCY_KEY_INVALID"),
                    expectedDialogueVersion,
                    rawTranscript,
                    finalTranscript
            );
        } catch (AiProviderException exception) {
            throw providerApiException(exception, "phân tích câu trả lời phỏng vấn");
        }
        if (properties.isTranscriptCorrectionEnabled()) {
            transcriptCorrectionCoordinator.submitAfterDecision(
                    parsedSessionId, result.confirmedTurnId());
        }
        CompletableFuture<Void> evidenceJob = submitTurnEvidence(parsedSessionId, result);
        startAdaptivePrefetchAfterThirdCoreAnswer(parsedSessionId, result, evidenceJob);
        if (result.itemCompleted()) {
            continueConversation(parsedSessionId);
        }
        prefetchCurrentConversationSpeech(parsedSessionId);
        return responseAssembler.assemble(requireSession(sessionId));
    }

    public AiInterviewSessionResponse skipConversationTurn(
            String sessionId,
            String turnId,
            String idempotencyKey,
            Integer expectedDialogueVersion
    ) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "answer");
        UUID parsedSessionId = parseUuid(sessionId, "SESSION_ID_INVALID");
        AiInterviewConversationService.ConversationResult result = conversationService.skipTurn(
                parsedSessionId,
                parseUuid(turnId, "TURN_ID_INVALID"),
                parseUuid(idempotencyKey, "IDEMPOTENCY_KEY_INVALID"),
                expectedDialogueVersion
        );
        if (result.itemCompleted()) {
            continueConversation(parsedSessionId);
        }
        prefetchCurrentConversationSpeech(parsedSessionId);
        return responseAssembler.assemble(requireSession(sessionId));
    }

    public AiInterviewSessionResponse replayConversationTurn(
            String sessionId,
            String turnId,
            Integer expectedDialogueVersion
    ) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "speech-replay");
        conversationService.replayTurn(
                parseUuid(sessionId, "SESSION_ID_INVALID"),
                parseUuid(turnId, "TURN_ID_INVALID"),
                expectedDialogueVersion
        );
        return responseAssembler.assemble(requireSession(sessionId));
    }

    public AiInterviewSessionResponse retryConversation(String sessionId) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "conversation-retry");
        InterviewSession session = requireMutableSession(sessionId);
        UUID parsedSessionId = session.getId();
        String stage = session.getLastErrorStage();
        if (AiInterviewConversationService.ERROR_STAGE_ANALYSIS.equals(stage)) {
            AiInterviewConversationService.ConversationResult result;
            try {
                result = conversationService.retryAnalysis(parsedSessionId);
            } catch (AiProviderException exception) {
                throw providerApiException(exception, "phân tích lại câu trả lời phỏng vấn");
            }
            if (properties.isTranscriptCorrectionEnabled()) {
                transcriptCorrectionCoordinator.submitAfterDecision(
                        parsedSessionId, result.confirmedTurnId());
            }
            CompletableFuture<Void> evidenceJob = submitTurnEvidence(parsedSessionId, result);
            startAdaptivePrefetchAfterThirdCoreAnswer(parsedSessionId, result, evidenceJob);
            if (result.itemCompleted()) {
                continueConversation(parsedSessionId);
            }
        } else if (AiInterviewConversationService.ERROR_STAGE_ADAPTIVE.equals(stage)) {
            generateAdaptiveForConversation(parsedSessionId);
            advanceConversation(parsedSessionId);
        } else if (AiInterviewConversationService.ERROR_STAGE_EVALUATION.equals(stage)) {
            evaluateConversation(parsedSessionId);
        } else {
            throw new ApiException(HttpStatus.CONFLICT, "INTERVIEW_RETRY_NOT_AVAILABLE",
                    "Không có thao tác hội thoại nào đang chờ thử lại.");
        }
        prefetchCurrentConversationSpeech(parsedSessionId);
        return responseAssembler.assemble(requireSession(sessionId));
    }

    @Transactional
    public AiInterviewSessionResponse reviewConversationTranscript(
            String sessionId,
            String turnId,
            AiInterviewTranscriptReviewRequest request
    ) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "answer");
        transcriptReviewService.review(
                parseUuid(sessionId, "SESSION_ID_INVALID"),
                parseUuid(turnId, "TURN_ID_INVALID"),
                request.action(),
                request.captureId() == null
                        ? null
                        : parseUuid(request.captureId(), "CAPTURE_ID_INVALID"),
                request.captureVersion(),
                request.transcript(),
                request.expectedEditCount());
        return responseAssembler.assemble(requireSession(sessionId));
    }

    public AiInterviewSessionResponse completeConversationTranscriptReview(String sessionId) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "summary");
        UUID parsedSessionId = parseUuid(sessionId, "SESSION_ID_INVALID");
        transcriptReviewService.completeReview(parsedSessionId);
        evaluateConversation(parsedSessionId);
        return responseAssembler.assemble(requireSession(sessionId));
    }

    public AiInterviewSessionResponse confirmAnswer(
            String sessionId, String questionId, String rawTranscript, String finalTranscript) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "answer");
        InterviewSession session = inTransaction(() -> {
            InterviewSession currentSession = requireMutableSession(sessionId);
            requireLegacyQuestionFlow(currentSession);
            InterviewQuestion question = requireQuestion(currentSession, questionId);
            confirmAnswer(currentSession, question, rawTranscript, finalTranscript, false);
            return currentSession;
        });
        if (!hasOpenQuestion(session)) {
            createNextQuestion(session);
        }
        return responseAssembler.assemble(requireSession(sessionId));
    }

    public AiInterviewSessionResponse finishInterview(String sessionId, String questionId, String transcript) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "finish");
        InterviewSession session = requireSession(sessionId);
        requireLegacyQuestionFlow(session);
        if (session.isCompleted()) {
            return responseAssembler.assemble(session);
        }

        if (hasText(transcript)) {
            if (!hasText(questionId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "QUESTION_ID_REQUIRED", "Cần xác định câu hỏi cho phần trả lời hiện tại");
            }
            session = inTransaction(() -> {
                InterviewSession currentSession = requireMutableSession(sessionId);
                InterviewQuestion question = requireQuestion(currentSession, questionId);
                confirmAnswer(currentSession, question, transcript, false);
                return currentSession;
            });
        }

        List<InterviewAnswer> answers = evaluableAnswers(session);
        if (answers.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INTERVIEW_ANSWER_REQUIRED", "Hãy trả lời ít nhất một câu trước khi kết thúc phỏng vấn");
        }

        evaluateWholeInterview(session.getId());
        return responseAssembler.assemble(requireSession(sessionId));
    }

    private void confirmAnswer(InterviewSession session,
                               InterviewQuestion question,
                               String transcript,
                               boolean advanceToNextQuestion) {
        confirmAnswer(session, question, null, transcript, advanceToNextQuestion);
    }

    private void confirmAnswer(InterviewSession session,
                               InterviewQuestion question,
                               String rawTranscript,
                               String finalTranscript,
                               boolean advanceToNextQuestion) {
        String normalizedTranscript = finalTranscript == null ? "" : finalTranscript.trim();
        if (normalizedTranscript.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANSWER_REQUIRED", "Câu trả lời không được để trống");
        }
        InterviewAnswer answer = answerRepository.findBySessionIdAndQuestionId(session.getId(), question.getId())
                .orElseGet(() -> draftAnswer(session, question));
        if (answer.getAnsweredAt() != null) {
            if (!answer.isSkipped() && normalizedTranscript.equals(answer.getTranscriptText())) {
                return;
            }
            throw new ApiException(HttpStatus.CONFLICT, "QUESTION_ALREADY_ANSWERED", "Câu hỏi này đã được chốt câu trả lời");
        }
        String persistedRaw = hasText(answer.getRawTranscript())
                ? answer.getRawTranscript().trim()
                : rawTranscript == null ? null : rawTranscript.trim();
        String reviewDraft = hasText(answer.getFinalTranscript())
                ? answer.getFinalTranscript().trim()
                : persistedRaw;
        if (hasText(persistedRaw)) {
            answer.setRawTranscript(persistedRaw);
        }
        answer.setTranscriptText(normalizedTranscript);
        answer.setFinalTranscript(normalizedTranscript);
        boolean edited = hasText(reviewDraft) && !normalizeTranscriptForAudit(reviewDraft)
                .equals(normalizeTranscriptForAudit(normalizedTranscript));
        answer.setTranscriptEdited(edited);
        answer.setTranscriptEditCount(edited ? Math.max(1, answer.getTranscriptEditCount() + 1) : answer.getTranscriptEditCount());
        answer.setConversationState("ANSWER_CONFIRMED");
        answer.setSkipped(false);
        answer.setTranscriptStatus("completed");
        answer.setFeedbackStatus("pending");
        answer.setAnsweredAt(LocalDateTime.now());
        answer.setErrorMessage(null);
        answer.setEvaluationSource("provider");
        answer.setEvaluationFallback(false);
        saveAnswerClaim(answer);

        if (advanceToNextQuestion && !hasOpenQuestion(session)) {
            createNextQuestion(session);
        }
    }

    public AiInterviewSessionResponse skipQuestion(String sessionId, String questionId) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "answer");
        InterviewSession session = inTransaction(() -> {
            InterviewSession currentSession = requireMutableSession(sessionId);
            requireLegacyQuestionFlow(currentSession);
            InterviewQuestion question = requireQuestion(currentSession, questionId);
            InterviewAnswer answer = answerRepository.findBySessionIdAndQuestionId(currentSession.getId(), question.getId())
                    .orElseGet(() -> draftAnswer(currentSession, question));
            if (answer.getAnsweredAt() != null) {
                if (answer.isSkipped()) {
                    return currentSession;
                }
                throw new ApiException(HttpStatus.CONFLICT, "QUESTION_ALREADY_ANSWERED", "Cau hoi nay da duoc chot cau tra loi");
            }
            answer.setTranscriptText("[SKIPPED]");
            answer.setFinalTranscript("[SKIPPED]");
            answer.setConversationState("ANSWER_CONFIRMED");
            answer.setSkipped(true);
            answer.setTranscriptStatus("completed");
            answer.setFeedbackStatus("completed");
            answer.setAnsweredAt(LocalDateTime.now());
            answer.setErrorMessage(null);
            answer.setEvaluationSource("skipped");
            answer.setEvaluationFallback(false);
            saveAnswerClaim(answer);
            return currentSession;
        });
        if (!hasOpenQuestion(session)) {
            createNextQuestion(session);
        }
        return responseAssembler.assemble(requireSession(sessionId));
    }

    public AiInterviewSessionResponse retryQuestionGeneration(String sessionId) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "question");
        InterviewSession session = requireMutableSession(sessionId);
        if (!hasOpenQuestion(session)) {
            createNextQuestion(session);
        }
        return responseAssembler.assemble(requireSession(sessionId));
    }

    public AiInterviewSessionResponse retrySummary(String sessionId) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "summary");
        InterviewSession session = requireSession(sessionId);
        if (evaluableAnswers(session).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SESSION_NOT_READY_FOR_SUMMARY", "Chưa có câu trả lời để tổng kết");
        }
        evaluateWholeInterview(session.getId());
        return responseAssembler.assemble(requireSession(sessionId));
    }

    public AiInterviewSessionResponse retryFeedback(String sessionId, String questionId) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "feedback");
        InterviewSession session = requireSession(sessionId);
        InterviewQuestion question = requireQuestion(session, questionId);
        InterviewAnswer answer = answerRepository.findBySessionIdAndQuestionId(session.getId(), question.getId())
                .filter(item -> item.getAnsweredAt() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "ANSWER_NOT_READY", "Chưa có câu trả lời để chấm lại"));
        if (answer.isSkipped()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SKIPPED_ANSWER", "Câu hỏi đã bỏ qua không cần chấm lại");
        }
        evaluateWholeInterview(session.getId());
        return responseAssembler.assemble(requireSession(sessionId));
    }

    private void continueConversation(UUID sessionId) {
        AiInterviewConversationService.AdvanceResult advance =
                conversationService.advanceAfterCompletedItem(sessionId);
        if (advance == AiInterviewConversationService.AdvanceResult.NEEDS_ADAPTIVE_QUESTIONS) {
            adaptivePrefetchCoordinator.await(sessionId, properties.getProviderReadTimeoutMs());
            advance = conversationService.advanceAfterCompletedItem(sessionId);
            if (advance == AiInterviewConversationService.AdvanceResult.NEEDS_ADAPTIVE_QUESTIONS) {
                generateAdaptiveForConversation(sessionId);
                advance = conversationService.advanceAfterCompletedItem(sessionId);
            }
            adaptivePrefetchCoordinator.forget(sessionId);
        }
        if (advance == AiInterviewConversationService.AdvanceResult.READY_TO_EVALUATE) {
            evaluateConversation(sessionId);
        }
    }

    private void startAdaptivePrefetchAfterThirdCoreAnswer(
            UUID sessionId,
            AiInterviewConversationService.ConversationResult result,
            CompletableFuture<Void> evidenceJob
    ) {
        if (result == null || result.idempotent() || result.assessmentItemOrder() != 3) return;
        adaptivePrefetchCoordinator.startAfter(sessionId, evidenceJob,
                () -> generateAdaptiveForConversation(sessionId));
    }

    private CompletableFuture<Void> submitTurnEvidence(
            UUID sessionId,
            AiInterviewConversationService.ConversationResult result
    ) {
        if (result == null || result.idempotent() || !result.evidenceEnrichmentRequired()
                || result.confirmedTurnId() == null || result.answerClientId() == null) {
            return CompletableFuture.completedFuture(null);
        }
        return turnEvidenceCoordinator.submit(
                sessionId, result.confirmedTurnId(), result.answerClientId());
    }

    private void advanceConversation(UUID sessionId) {
        AiInterviewConversationService.AdvanceResult advance =
                conversationService.advanceAfterCompletedItem(sessionId);
        if (advance == AiInterviewConversationService.AdvanceResult.READY_TO_EVALUATE) {
            evaluateConversation(sessionId);
        }
    }

    private void generateAdaptiveForConversation(UUID sessionId) {
        try {
            InterviewSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND",
                            "Không tìm thấy phiên phỏng vấn"));
            List<InterviewQuestion> questions = questionRepository
                    .findBySessionIdOrderByOrderIndexAsc(sessionId);
            List<InterviewAnswer> answers = answerRepository
                    .findBySessionIdOrderByAnsweredAtAsc(sessionId);
            long completedAnswers = answers.stream()
                    .filter(answer -> answer.getAnsweredAt() != null)
                    .count();
            boolean thirdCoreHasConfirmedTurn = questions.size() == 3
                    && conversationTurnRepository
                    .findBySessionIdAndAssessmentItemIdOrderBySequenceNoAsc(
                            sessionId, questions.get(2).getId())
                    .stream()
                    .anyMatch(turn -> turn.getAnswerStatus() == InterviewTurnAnswerStatus.CONFIRMED
                            && hasText(turn.getCandidateFinalAnswer()));
            boolean ready = session.getDialogueState() == null
                    ? completedAnswers >= 3
                    : completedAnswers >= 2 && thirdCoreHasConfirmedTurn;
            if (questions.size() != 3 || !ready) {
                throw new ApiException(HttpStatus.CONFLICT, "ADAPTIVE_QUESTIONS_NOT_READY",
                        "Chưa có đủ evidence từ ba assessment item đầu để tạo batch thích ứng.");
            }
            createAdaptiveQuestionBatch(session, questions, answers);
        } catch (ApiException exception) {
            if (isRecoverableAiFailure(exception)) {
                log.warn("AI adaptive question fallback applied: sessionId={}, code={}, detail=\"{}\"",
                        sessionId, exception.getCode(), exception.getMessage());
                createFallbackAdaptiveQuestionBatch(sessionId);
                return;
            }
            conversationService.markFailure(
                    sessionId,
                    AiInterviewConversationService.ERROR_STAGE_ADAPTIVE,
                    exception.getCode(),
                    exception.getMessage()
            );
            throw exception;
        }
    }

    private void createFallbackAdaptiveQuestionBatch(UUID sessionId) {
        InterviewSession session = requireSession(sessionId.toString());
        List<InterviewQuestion> questions = questionRepository
                .findBySessionIdOrderByOrderIndexAsc(sessionId);
        createFallbackAdaptiveQuestionBatch(session, questions);
    }

    private void createFallbackAdaptiveQuestionBatch(
            InterviewSession session,
            List<InterviewQuestion> questions
    ) {
        int remaining = Math.max(0, properties.effectiveCoreQuestionCount() - questions.size());
        if (remaining == 0) return;
        List<ShopAiKeyClient.RubricQuestionDraft> drafts = fallbackFactory.adaptiveQuestions(
                session, questions, remaining);
        persistAdaptiveBatchAtomically(
                session.getId(),
                drafts,
                questions.size() + 1,
                session.getEvaluationProfile(),
                AiInterviewFallbackFactory.FALLBACK_PROMPT_VERSION
        );
        prefetchQuestions(drafts);
    }

    private void evaluateConversation(UUID sessionId) {
        try {
            evaluateWholeInterview(sessionId);
        } catch (ApiException exception) {
            conversationService.markFailure(
                    sessionId,
                    AiInterviewConversationService.ERROR_STAGE_EVALUATION,
                    exception.getCode(),
                    exception.getMessage()
            );
            throw exception;
        }
    }

    private void evaluateWholeInterview(UUID sessionId) {
        WholeInterviewEvaluationContext context = inTransaction(() -> {
            InterviewSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND",
                            "Không tìm thấy phiên phỏng vấn"));
            prepareSessionForProvider(session);
            List<InterviewQuestion> questions = questionRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);
            List<InterviewAnswer> answers = answerRepository.findBySessionIdOrderByAnsweredAtAsc(sessionId).stream()
                    .filter(answer -> answer.getAnsweredAt() != null)
                    .toList();
            if (answers.stream().noneMatch(answer -> !answer.isSkipped())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INTERVIEW_ANSWER_REQUIRED",
                        "Hãy trả lời ít nhất một câu trước khi chấm điểm");
            }
            List<ShopAiKeyClient.GroupedAssessmentEvidence> groupedEvidence =
                    session.getDialogueState() == null
                            ? List.of()
                            : groupedAssessmentEvidence(session, questions, answers);
            return new WholeInterviewEvaluationContext(
                    session, questions, answers, groupedEvidence);
        });

        ShopAiKeyClient.InterviewEvaluationDraft evaluation;
        boolean evaluationFallback = false;
        try {
            evaluation = context.groupedEvidence().isEmpty()
                    ? shopAiKeyClient.evaluateInterview(
                    context.session(), context.questions(),
                    context.answers().stream().filter(answer -> !answer.isSkipped()).toList())
                    : shopAiKeyClient.evaluateGroupedInterview(
                    context.session(), context.groupedEvidence());
        } catch (AiProviderException exception) {
            log.warn("AI final evaluation fallback applied: sessionId={}, code={}, detail=\"{}\"",
                    sessionId, exception.getCode(), exception.getMessage());
            evaluation = fallbackFactory.finalEvaluation(
                    context.groupedEvidence(), context.questions(), context.answers());
            evaluationFallback = true;
        }

        List<UUID> answerIds = context.answers().stream()
                .map(InterviewAnswer::getId)
                .filter(Objects::nonNull)
                .toList();
        List<InterviewAnswerCapture> captures = voiceEvidenceService.awaitAndLoad(answerIds);
        AiInterviewScoreCalculator.ScoreResult score = captures.isEmpty()
                ? scoreCalculator.calculate(
                context.session().getEvaluationProfile(),
                context.questions(),
                context.answers(),
                evaluation.questionRatings())
                : scoreCalculator.calculate(
                context.session().getEvaluationProfile(),
                context.questions(),
                context.answers(),
                evaluation.questionRatings(),
                captures);

        boolean usedEvaluationFallback = evaluationFallback;
        ShopAiKeyClient.InterviewEvaluationDraft resolvedEvaluation = evaluation;
        inTransaction(() -> {
            InterviewSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND",
                            "Không tìm thấy phiên phỏng vấn"));
            Map<UUID, ShopAiKeyClient.QuestionRatingDraft> ratings = resolvedEvaluation.questionRatings().stream()
                    .collect(Collectors.toMap(rating -> UUID.fromString(rating.questionId()), rating -> rating));
            for (InterviewAnswer answer : context.answers()) {
                AiInterviewScoreCalculator.QuestionScoreResult questionResult =
                        score.questionResults().get(answer.getQuestionId());
                if (questionResult == null) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_INTERVIEW_EVALUATION",
                            "Thiếu kết quả chấm deterministic cho một câu hỏi");
                }
                AiAnswerFeedback feedback = answerFeedbackRepository.findByAnswerId(answer.getId())
                        .orElseGet(AiAnswerFeedback::new);
                feedback.setAnswer(answer);
                feedback.setOverallScore(questionResult.questionScore());
                feedback.setEvaluationStatus(questionResult.evaluationStatus());
                feedback.setBarsLevel(questionResult.barsLevel());
                feedback.setScoreReason(questionResult.scoreReason());
                if (answer.isSkipped()) {
                    feedback.setFeedback("Câu hỏi không được đánh giá vì ứng viên đã bỏ qua.");
                    feedback.setStrengths(null);
                    feedback.setWeaknesses(null);
                    feedback.setSuggestions(null);
                    feedback.setModelUsed(null);
                } else {
                    ShopAiKeyClient.QuestionRatingDraft rating = ratings.get(answer.getQuestionId());
                    if (rating == null) {
                        throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_INTERVIEW_EVALUATION",
                                "AI thiếu đánh giá cho một câu trả lời");
                    }
                    feedback.setFeedback(usedEvaluationFallback
                            ? fallbackEvaluationFeedback(rating)
                            : evaluationFeedback(rating));
                    feedback.setStrengths(joinLines(rating.evidence()));
                    feedback.setWeaknesses(joinLines(rating.missingEvidence()));
                    feedback.setSuggestions(joinLines(rating.missingEvidence()));
                    feedback.setModelUsed(usedEvaluationFallback ? null
                            : properties.getTextAi().getFinalEvaluation().getModel());
                }
                answerFeedbackRepository.save(feedback);
                answer.setFeedbackStatus("completed");
                answer.setEvaluationSource(answer.isSkipped()
                        ? "skipped"
                        : usedEvaluationFallback ? "fallback" : "provider");
                answer.setEvaluationFallback(!answer.isSkipped() && usedEvaluationFallback);
                answer.setErrorMessage(null);
                answerRepository.save(answer);
            }

            AiSessionFeedback summary = sessionFeedbackRepository.findBySessionId(sessionId)
                    .orElseGet(AiSessionFeedback::new);
            summary.setSession(session);
            summary.setOverallScore(score.overallScore());
            summary.setContentScore(score.contentScore());
            summary.setVoiceDeliveryScore(score.voiceDeliveryScore());
            summary.setRawVoiceDeliveryScore(score.rawVoiceDeliveryScore());
            summary.setVoiceWeight(score.voiceWeight());
            summary.setReplayCount(score.replayCount());
            summary.setReplayPenalty(score.replayPenalty());
            summary.setVoiceEvidenceQuestionCount(score.voiceEvidenceQuestionCount());
            summary.setManualFallbackQuestionCount(score.manualFallbackQuestionCount());
            summary.setReferenceOnly(true);
            summary.setEvaluationProfileVersion(String.valueOf(
                    session.getEvaluationProfile().getOrDefault("profileVersion", EVALUATION_PROFILE_VERSION)));
            summary.setRubricVersion(BARS_RUBRIC_VERSION);
            summary.setSpeechCalibrationVersion(properties.getSpeechCalibrationVersion());
            summary.setAiSummary(resolvedEvaluation.summary());
            summary.setStrengths(joinLines(resolvedEvaluation.strengths()));
            summary.setWeaknesses(joinLines(resolvedEvaluation.improvements()));
            summary.setSuggestions(joinLines(resolvedEvaluation.actionPlan()));
            summary.setModelUsed(usedEvaluationFallback ? null
                    : properties.getTextAi().getFinalEvaluation().getModel());
            summary.setEvaluationSource(usedEvaluationFallback ? "fallback" : "provider");
            summary.setEvaluationFallback(usedEvaluationFallback);
            sessionFeedbackRepository.save(summary);
            session.setOverallScore(score.overallScore());
            session.setAiSummary(resolvedEvaluation.summary());
            session.setCompletedAt(LocalDateTime.now());
            session.setStatus("completed");
            if (session.getDialogueState() != null) {
                session.setDialogueState(InterviewDialogueState.COMPLETED);
                session.setDialogueVersion((session.getDialogueVersion() == null
                        ? 1 : session.getDialogueVersion()) + 1);
                session.setLastErrorStage(null);
                session.setLastErrorCode(null);
                session.setLastErrorMessage(null);
            }
            sessionRepository.save(session);
            return null;
        });
    }

    private String evaluationFeedback(ShopAiKeyClient.QuestionRatingDraft rating) {
        String evidence = rating.evidence().isEmpty()
                ? "Chưa ghi nhận bằng chứng rõ ràng."
                : "Bằng chứng: " + String.join("; ", rating.evidence()) + ".";
        String missing = rating.missingEvidence().isEmpty()
                ? ""
                : " Cần bổ sung: " + String.join("; ", rating.missingEvidence()) + ".";
        return "BARS " + rating.barsLevel() + "/5. " + evidence + missing;
    }

    private String fallbackEvaluationFeedback(ShopAiKeyClient.QuestionRatingDraft rating) {
        String evidence = rating.evidence().isEmpty()
                ? "Câu trả lời đã được ghi nhận."
                : String.join("; ", rating.evidence()) + ".";
        return "Đánh giá dự phòng BARS " + rating.barsLevel() + "/5. " + evidence
                + " Hãy xem đây là gợi ý luyện tập tham khảo.";
    }

    private List<ShopAiKeyClient.GroupedAssessmentEvidence> groupedAssessmentEvidence(
            InterviewSession session,
            List<InterviewQuestion> questions,
            List<InterviewAnswer> answers
    ) {
        Map<UUID, InterviewAnswer> answersByQuestion = answers.stream()
                .collect(Collectors.toMap(
                        InterviewAnswer::getQuestionId,
                        answer -> answer,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        List<ShopAiKeyClient.GroupedAssessmentEvidence> grouped = new ArrayList<>();
        for (InterviewQuestion question : questions) {
            InterviewAnswer answer = answersByQuestion.get(question.getId());
            if (answer == null || answer.isSkipped() || answer.getAnsweredAt() == null) {
                continue;
            }
            List<ShopAiKeyClient.EvidenceTurnDraft> evidenceTurns = conversationTurnRepository
                    .findBySessionIdAndAssessmentItemIdOrderBySequenceNoAsc(
                            session.getId(), question.getId())
                    .stream()
                    .filter(turn -> turn.getAnswerStatus() == InterviewTurnAnswerStatus.CONFIRMED)
                    .filter(turn -> turn.getTurnType() == InterviewTurnType.CORE_QUESTION
                            || turn.getTurnType() == InterviewTurnType.PROBE
                            || turn.getTurnType() == InterviewTurnType.CLARIFY)
                    .filter(turn -> hasText(turn.getCandidateFinalAnswer()))
                    .map(turn -> new ShopAiKeyClient.EvidenceTurnDraft(
                            turn.getTurnType().name(),
                            turn.getCandidateFinalAnswer()
                    ))
                    .toList();
            if (evidenceTurns.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "INTERVIEW_EVIDENCE_MISSING",
                        "Assessment item đã hoàn tất nhưng không có evidence turn để chấm.");
            }
            grouped.add(new ShopAiKeyClient.GroupedAssessmentEvidence(
                    question.getId().toString(),
                    question.getCompetencyId(),
                    question.getQuestionType(),
                    question.getContent(),
                    question.getRubric(),
                    evidenceTurns
            ));
        }
        return List.copyOf(grouped);
    }

    private boolean hasOpenQuestion(InterviewSession session) {
        return questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId())
                .stream()
                .anyMatch(question -> answerRepository.findBySessionIdAndQuestionId(session.getId(), question.getId())
                        .map(answer -> answer.getAnsweredAt() == null)
                        .orElse(true));
    }

    private void createNextQuestion(InterviewSession session) {
        if (isFixedQuestionMode(session)) {
            return;
        }
        List<InterviewQuestion> questions = questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId());
        if (questions.size() >= properties.effectiveCoreQuestionCount()) {
            return;
        }
        List<InterviewAnswer> answers = answerRepository.findBySessionIdOrderByAnsweredAtAsc(session.getId());
        if (questions.isEmpty()) {
            createInitialQuestionBatch(session);
            return;
        }
        if (questions.size() == 3 && answers.stream().filter(answer -> answer.getAnsweredAt() != null).count() >= 3) {
            createAdaptiveQuestionBatch(session, questions, answers);
        }
    }

    private void createInitialQuestionBatch(InterviewSession session) {
        persistInitialPackage(
                session,
                shopAiKeyClient.generateInitialInterviewPackage(session, session.getCandidate()),
                INITIAL_QUESTION_PROMPT_VERSION
        );
    }

    private void persistInitialPackage(
            InterviewSession session,
            ShopAiKeyClient.InterviewPackageDraft interviewPackage,
            String promptVersion
    ) {
        Map<String, Object> evaluationProfile = evaluationProfileMap(interviewPackage.evaluationProfile());
        session.setEvaluationProfile(evaluationProfile);
        persistQuestionBatch(
                session,
                interviewPackage.questions(),
                1,
                promptVersion,
                evaluationProfile
        );
    }

    private void createAdaptiveQuestionBatch(InterviewSession session,
                                             List<InterviewQuestion> questions,
                                             List<InterviewAnswer> answers) {
        try {
            createProviderAdaptiveQuestionBatch(session, questions, answers);
        } catch (ApiException exception) {
            if (!isRecoverableAiFailure(exception)) throw exception;
            log.warn("AI adaptive question fallback applied: sessionId={}, code={}, detail=\"{}\"",
                    session.getId(), exception.getCode(), exception.getMessage());
            createFallbackAdaptiveQuestionBatch(session, questions);
        }
    }

    private void createProviderAdaptiveQuestionBatch(InterviewSession session,
                                                     List<InterviewQuestion> questions,
                                                     List<InterviewAnswer> answers) {
        List<ShopAiKeyClient.RubricQuestionDraft> drafts =
                generateAdaptiveQuestionDrafts(session, questions, answers, false);
        List<ShopAiKeyClient.RubricQuestionDraft> persistedDrafts = drafts;
        try {
            persistAdaptiveBatchAtomically(
                    session.getId(),
                    drafts,
                    questions.size() + 1,
                    session.getEvaluationProfile()
            );
        } catch (ApiException exception) {
            if (!"AI_INCOMPLETE_SCORED_COMPETENCY_COVERAGE".equals(exception.getCode())) {
                throw exception;
            }
            List<ShopAiKeyClient.RubricQuestionDraft> correctedDrafts =
                    generateAdaptiveQuestionDrafts(session, questions, answers, true);
            persistAdaptiveBatchAtomically(
                    session.getId(),
                    correctedDrafts,
                    questions.size() + 1,
                    session.getEvaluationProfile()
            );
            persistedDrafts = correctedDrafts;
        }
        prefetchQuestions(persistedDrafts);
    }

    private void persistAdaptiveBatchAtomically(
            UUID sessionId,
            List<ShopAiKeyClient.RubricQuestionDraft> drafts,
            int firstOrderIndex,
            Map<String, Object> evaluationProfile
    ) {
        persistAdaptiveBatchAtomically(
                sessionId,
                drafts,
                firstOrderIndex,
                evaluationProfile,
                ADAPTIVE_QUESTION_PROMPT_VERSION
        );
    }

    private void persistAdaptiveBatchAtomically(
            UUID sessionId,
            List<ShopAiKeyClient.RubricQuestionDraft> drafts,
            int firstOrderIndex,
            Map<String, Object> evaluationProfile,
            String promptVersion
    ) {
        inTransaction(() -> {
            InterviewSession lockedSession = sessionRepository.findByIdForUpdate(sessionId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND",
                            "Không tìm thấy phiên phỏng vấn."));
            List<InterviewQuestion> currentQuestions = questionRepository
                    .findBySessionIdOrderByOrderIndexAsc(sessionId);
            if (currentQuestions.size() >= properties.effectiveCoreQuestionCount()) {
                return null;
            }
            if (currentQuestions.size() != 3 || firstOrderIndex != 4) {
                throw new ApiException(HttpStatus.CONFLICT, "ADAPTIVE_QUESTION_STATE_CHANGED",
                        "Trạng thái batch câu hỏi thích ứng đã thay đổi.");
            }
            persistQuestionBatch(
                    lockedSession,
                    drafts,
                    firstOrderIndex,
                    promptVersion,
                    evaluationProfile
            );
            return null;
        });
    }

    private List<ShopAiKeyClient.RubricQuestionDraft> generateAdaptiveQuestionDrafts(
            InterviewSession session,
            List<InterviewQuestion> questions,
            List<InterviewAnswer> answers,
            boolean coverageCorrection) {
        try {
            return coverageCorrection
                    ? shopAiKeyClient.regenerateAdaptiveInterviewQuestionsForCoverage(session, questions, answers)
                    : shopAiKeyClient.generateAdaptiveInterviewQuestions(session, questions, answers);
        } catch (AiProviderException exception) {
            throw providerApiException(exception, "tạo câu hỏi phỏng vấn thích ứng");
        }
    }

    private void prefetchQuestions(List<ShopAiKeyClient.RubricQuestionDraft> drafts) {
        try {
            speechPrefetchService.prefetch(drafts.stream()
                    .map(ShopAiKeyClient.RubricQuestionDraft::question)
                    .toList());
        } catch (RuntimeException exception) {
            log.warn("AI interview speech prefetch skipped: exceptionType={}, detail=\"{}\"",
                    exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    private void prefetchInitialConversationSpeech(InterviewSession session) {
        try {
            String speechText = responseAssembler.currentConversationSpeech(session);
            speechPrefetchService.prefetchSegmentsAndAwait(
                    speechText,
                    properties.getTtsPrefetchInitialWaitMs());
            List<String> coreQuestions = questionRepository
                    .findBySessionIdOrderByOrderIndexAsc(session.getId())
                    .stream()
                    .map(InterviewQuestion::getContent)
                    .toList();
            speechPrefetchService.prefetch(
                    conversationTemplates.initialPriorityPhrases(coreQuestions));
        } catch (RuntimeException exception) {
            log.warn("AI interview initial speech prefetch skipped: sessionId={}, exceptionType={}, detail=\"{}\"",
                    session.getId(),
                    exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    private void prefetchCurrentConversationSpeech(UUID sessionId) {
        try {
            InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
            if (session == null) return;
            String speechText = responseAssembler.currentConversationSpeech(session);
            if (speechText != null && !speechText.isBlank()) {
                speechPrefetchService.prefetch(List.of(
                        speechText,
                        conversationTemplates.confirmationPrompt()
                ));
            }
        } catch (RuntimeException exception) {
            log.warn("AI interview current speech prefetch skipped: sessionId={}, exceptionType={}, detail=\"{}\"",
                    sessionId, exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    private void persistQuestionBatch(InterviewSession session,
                                      List<ShopAiKeyClient.RubricQuestionDraft> drafts,
                                      int firstOrderIndex,
                                      String promptVersion,
                                      Map<String, Object> evaluationProfile) {
        Set<String> competencyIds = scoredCompetencyIds(evaluationProfile);
        validateQuestionCoverage(session, drafts, firstOrderIndex, competencyIds);
        int orderIndex = firstOrderIndex;
        for (ShopAiKeyClient.RubricQuestionDraft draft : drafts) {
            if (!competencyIds.contains(draft.competencyId())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_UNKNOWN_COMPETENCY",
                        "AI tạo câu hỏi không thuộc Evaluation Profile đã lưu.");
            }
            InterviewQuestion question = new InterviewQuestion();
            question.setSession(session);
            question.setOrderIndex(orderIndex++);
            question.setQuestionType(draft.questionType());
            question.setDifficulty(draft.difficulty());
            question.setSkillTag(draft.skillTag());
            question.setContent(draft.question());
            question.setTimeLimitSeconds(draft.timeLimitSeconds());
            question.setAiGenerated(true);
            question.setSourceType(AI_QUESTION_SOURCE);
            question.setSourceId(null);
            question.setPromptVersion(promptVersion);
            question.setRubricVersion(BARS_RUBRIC_VERSION);
            question.setCompetencyId(draft.competencyId());
            question.setRubric(rubricMap(draft));
            questionRepository.save(question);
        }
        session.setTotalQuestions(Math.min(properties.effectiveCoreQuestionCount(), orderIndex - 1));
        sessionRepository.save(session);
    }

    private Map<String, Object> evaluationProfileMap(ShopAiKeyClient.EvaluationProfileDraft draft) {
        if (draft.competencies() == null || draft.competencies().size() < 3 || draft.competencies().size() > 6) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_EVALUATION_PROFILE",
                    "Evaluation Profile phải có từ 3 đến 6 năng lực.");
        }
        Set<String> allCompetencyIds = draft.competencies().stream()
                .map(ShopAiKeyClient.CompetencyDraft::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (allCompetencyIds.size() != draft.competencies().size()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_DUPLICATE_COMPETENCY",
                    "Evaluation Profile chứa competencyId trùng lặp.");
        }
        if (draft.scoredCompetencyIds() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_SCORED_COMPETENCY_SET",
                    "Evaluation Profile thiếu Scored Competency Set.");
        }
        List<Map<String, Object>> competencies = new ArrayList<>();
        Set<String> scoredIds = new LinkedHashSet<>(draft.scoredCompetencyIds());
        if (scoredIds.size() < 3 || scoredIds.size() > 4
                || scoredIds.size() != draft.scoredCompetencyIds().size()
                || !allCompetencyIds.containsAll(scoredIds)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_SCORED_COMPETENCY_SET",
                    "Scored Competency Set phải có 3 đến 4 competencyId hợp lệ và không trùng lặp.");
        }
        double prioritySum = draft.competencies().stream()
                .filter(competency -> scoredIds.contains(competency.id()))
                .mapToDouble(this::competencyPriority)
                .sum();
        for (ShopAiKeyClient.CompetencyDraft competency : draft.competencies()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", competency.id());
            item.put("name", competency.name());
            item.put("definition", competency.definition());
            item.put("importance", competency.importance());
            item.put("entryNeedScore", competency.entryNeedScore());
            item.put("distinguishingValueScore", competency.distinguishingValueScore());
            item.put("priority", competencyPriority(competency));
            item.put("scored", scoredIds.contains(competency.id()));
            item.put("scoredWeight", !scoredIds.contains(competency.id()) || prioritySum == 0.0
                    ? 0.0
                    : competencyPriority(competency) / prioritySum);
            item.put("measurementMode", competency.measurementMode());
            item.put("rationale", competency.rationale());
            competencies.add(item);
        }
        int communicationDemand = Math.max(1, Math.min(5, draft.communicationDemand()));
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("targetRole", draft.targetRole());
        profile.put("seniority", draft.seniority());
        profile.put("communicationDemand", communicationDemand);
        profile.put("voiceWeight", 0.05 + communicationDemand * 0.05);
        profile.put("scoredCompetencyIds", List.copyOf(draft.scoredCompetencyIds()));
        profile.put("competencies", competencies);
        profile.put("priorityFormulaVersion", "directional-priority-v2");
        profile.put("profileVersion", EVALUATION_PROFILE_VERSION);
        return profile;
    }

    private double competencyPriority(ShopAiKeyClient.CompetencyDraft competency) {
        return 0.50 * competency.importance()
                + 0.20 * competency.entryNeedScore()
                + 0.30 * competency.distinguishingValueScore();
    }

    private Set<String> scoredCompetencyIds(Map<String, Object> evaluationProfile) {
        Object value = evaluationProfile == null ? null : evaluationProfile.get("scoredCompetencyIds");
        if (!(value instanceof Collection<?> ids)) {
            return Set.of();
        }
        return ids.stream()
                .map(String::valueOf)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateQuestionCoverage(InterviewSession session,
                                          List<ShopAiKeyClient.RubricQuestionDraft> drafts,
                                          int firstOrderIndex,
                                          Set<String> scoredIds) {
        if (scoredIds.size() < 3 || scoredIds.size() > 4) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_SCORED_COMPETENCY_SET",
                    "Evaluation Profile phải có từ 3 đến 4 năng lực được chấm.");
        }
        Set<String> draftIds = drafts.stream()
                .map(ShopAiKeyClient.RubricQuestionDraft::competencyId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!scoredIds.containsAll(draftIds)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_UNKNOWN_COMPETENCY",
                    "AI tạo câu hỏi ngoài Scored Competency Set đã lưu.");
        }
        if (firstOrderIndex == 1 && draftIds.size() != 3) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_INITIAL_COVERAGE",
                    "Ba câu đầu phải ưu tiên ba năng lực chính khác nhau.");
        }
        if (firstOrderIndex > 1) {
            Set<String> coveredIds = questionRepository
                    .findBySessionIdOrderByOrderIndexAsc(session.getId())
                    .stream()
                    .map(InterviewQuestion::getCompetencyId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            coveredIds.addAll(draftIds);
            if (!coveredIds.containsAll(scoredIds)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INCOMPLETE_SCORED_COMPETENCY_COVERAGE",
                        "Năm câu hỏi phải cover toàn bộ Scored Competency Set.");
            }
        }
    }

    private Map<String, Object> rubricMap(ShopAiKeyClient.RubricQuestionDraft draft) {
        Map<String, Object> bars = new LinkedHashMap<>();
        bars.put("level1", draft.barsLevel1());
        bars.put("level3", draft.barsLevel3());
        bars.put("level5", draft.barsLevel5());
        Map<String, Object> rubric = new LinkedHashMap<>();
        rubric.put("expectedEvidence", draft.expectedEvidence());
        rubric.put("bars", bars);
        return rubric;
    }

    private void copyFixedQuestions(InterviewSession session, List<AiQuestionBank> fixedQuestions) {
        int orderIndex = 1;
        for (AiQuestionBank fixedQuestion : fixedQuestions) {
            InterviewQuestion question = new InterviewQuestion();
            question.setSession(session);
            question.setOrderIndex(orderIndex++);
            question.setQuestionType(fixedQuestion.getQuestionType());
            question.setDifficulty(fixedQuestion.getDifficulty());
            question.setSkillTag(fixedQuestion.getSkillTag());
            question.setContent(fixedQuestion.getContent());
            question.setTimeLimitSeconds(fixedQuestion.getTimeLimitSeconds());
            question.setAiGenerated(false);
            question.setSourceType(QUESTION_BANK_SOURCE);
            question.setSourceId(fixedQuestion.getId().toString());
            question.setPromptVersion(null);
            question.setRubricVersion(null);
            question.setCompetencyId(null);
            question.setRubric(Map.of());
            questionRepository.save(question);
        }
        session.setTotalQuestions(fixedQuestions.size());
        sessionRepository.save(session);
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

    private String normalizeTranscriptForAudit(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private InterviewQuestion currentQuestion(InterviewSession session) {
        List<InterviewQuestion> questions = questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId());
        return questions.stream()
                .filter(question -> answerRepository.findBySessionIdAndQuestionId(session.getId(), question.getId())
                        .map(answer -> answer.getAnsweredAt() == null)
                        .orElse(true))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_OPEN_QUESTION", "Không có câu hỏi đang mở"));
    }

    private List<InterviewAnswer> evaluableAnswers(InterviewSession session) {
        return answerRepository.findBySessionIdOrderByAnsweredAtAsc(session.getId())
                .stream()
                .filter(answer -> answer.getAnsweredAt() != null)
                .filter(answer -> !answer.isSkipped())
                .filter(answer -> hasText(answer.getTranscriptText()))
                .toList();
    }

    private boolean isEligibleApplication(Application application) {
        Application.ApplicationStatus status = application.getStatusEnum();
        return List.of(
                Application.ApplicationStatus.SUBMITTED,
                Application.ApplicationStatus.UNDER_REVIEW,
                Application.ApplicationStatus.SHORTLISTED
        ).contains(status) && isActiveJob(application.getJob());
    }

    private boolean isActiveJob(Job job) {
        return job != null
                && "published".equals(job.getStatus())
                && (job.getDeadline() == null || !job.getDeadline().isBefore(LocalDate.now()));
    }

    private boolean hasBasicProfile(CandidateProfile profile) {
        return hasText(profile.getFullName())
                || hasText(profile.getHeadline())
                || hasText(profile.getBio())
                || hasText(profile.getLocation())
                || (profile.getSkills() != null && !profile.getSkills().isEmpty());
    }

    private boolean isFixedQuestionMode(InterviewSession session) {
        Object questionMode = session.getPracticeContext() == null ? null : session.getPracticeContext().get("questionMode");
        return "fixed".equals(questionMode);
    }

    private void validateAudio(MultipartFile file, Integer durationSeconds) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_REQUIRED", "Vui lòng ghi âm câu trả lời");
        }
        if (file.getSize() > properties.audioMaxBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_TOO_LARGE", "File ghi âm vượt quá dung lượng cho phép");
        }
        if (durationSeconds == null || durationSeconds <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_DURATION_REQUIRED", "Không xác định được thời lượng ghi âm");
        }
        if (durationSeconds > properties.getAudioMaxSeconds()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_TOO_LONG", "Bạn ghi âm vượt quá thời lượng cho phép");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("audio/") && !contentType.contains("webm") && !contentType.contains("ogg")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_INVALID_TYPE", "Chỉ hỗ trợ file audio");
        }
        try {
            byte[] header = file.getInputStream().readNBytes(12);
            if (!hasSupportedAudioHeader(header)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_INVALID_CONTENT", "Nội dung file không phải định dạng audio được hỗ trợ");
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_UNREADABLE", "Không thể đọc file ghi âm");
        }
    }

    private boolean hasSupportedAudioHeader(byte[] header) {
        if (header.length < 4) {
            return false;
        }
        boolean wav = header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F';
        boolean ogg = header[0] == 'O' && header[1] == 'g' && header[2] == 'g' && header[3] == 'S';
        boolean mp3 = (header[0] == 'I' && header[1] == 'D' && header[2] == '3')
                || ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0);
        boolean webm = (header[0] & 0xFF) == 0x1A && (header[1] & 0xFF) == 0x45
                && (header[2] & 0xFF) == 0xDF && (header[3] & 0xFF) == 0xA3;
        return wav || ogg || mp3 || webm;
    }

    private InterviewSession requireSession(String sessionId) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        return sessionRepository.findByIdAndCandidateIdAndDeletedAtIsNull(parseUuid(sessionId, "SESSION_ID_INVALID"), candidate.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Không tìm thấy phiên phỏng vấn"));
    }

    private InterviewSession requireMutableSession(String sessionId) {
        InterviewSession session = requireSession(sessionId);
        if (session.isCompleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_COMPLETED", "Phiên phỏng vấn đã hoàn thành");
        }
        return session;
    }

    private void requireLegacyQuestionFlow(InterviewSession session) {
        if (session.getDialogueState() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "CONVERSATION_ENDPOINT_REQUIRED",
                    "Phiên này dùng luồng hội thoại; hãy thao tác bằng currentTurnId.");
        }
    }

    private void saveAnswerClaim(InterviewAnswer answer) {
        try {
            answerRepository.saveAndFlush(answer);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "ANSWER_OPERATION_IN_PROGRESS", "Câu trả lời đang được xử lý bởi một yêu cầu khác");
        }
    }

    private InterviewQuestion requireQuestion(InterviewSession session, String questionId) {
        return questionRepository.findByIdAndSessionId(parseUuid(questionId, "QUESTION_ID_INVALID"), session.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "Không tìm thấy câu hỏi"));
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_INTERVIEW_NOT_CONFIGURED", "AI Interview chưa được cấu hình.");
        }
        if (!isEnabledByAdmin()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_INTERVIEW_DISABLED", "Phỏng vấn AI đang bị tắt bởi quản trị viên.");
        }
    }

    private boolean isEnabledByAdmin() {
        return systemSettingsService == null || systemSettingsService.isAiInterviewEnabled();
    }

    private void prepareSessionForProvider(InterviewSession session) {
        Job job = session.getJob();
        if (job != null) {
            job.getTitle();
            job.getRequirementsText();
        }
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    private ApiException providerApiException(AiProviderException exception, String operation) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                exception.getCode(),
                "AI không thể " + operation + ": " + exception.getMessage()
        );
    }

    private boolean isRecoverableAiFailure(ApiException exception) {
        return exception != null
                && exception.getCode() != null
                && exception.getCode().startsWith("AI_");
    }

    private record WholeInterviewEvaluationContext(
            InterviewSession session,
            List<InterviewQuestion> questions,
            List<InterviewAnswer> answers,
            List<ShopAiKeyClient.GroupedAssessmentEvidence> groupedEvidence) {
    }

    private void requireAiSession(User user) {
        if (featureLimitService != null) {
            featureLimitService.requireAiSession(user);
        }
    }

    private void consumeAiSession(User user) {
        if (featureLimitService != null) {
            featureLimitService.consumeAiSession(user);
        }
    }

    private String joinLines(List<String> values) {
        return values == null ? "" : values.stream().filter(this::hasText).collect(Collectors.joining("\n"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private UUID parseUuid(String value, String code) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "Mã định danh không hợp lệ");
        }
    }
}
