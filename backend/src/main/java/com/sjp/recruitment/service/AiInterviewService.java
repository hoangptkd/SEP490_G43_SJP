package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.AiInterviewPracticeSessionRequest;
import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.model.entity.*;
import com.sjp.recruitment.repository.*;
import com.sjp.recruitment.service.ai.AiProviderException;
import com.sjp.recruitment.service.ai.GladiaTranscriptionClient;
import com.sjp.recruitment.service.ai.AiInterviewRateLimiter;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.io.IOException;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiInterviewService {

    private static final String PROVIDER_RETRY_MESSAGE = "Hệ thống chưa xử lý được câu trả lời này, vui lòng thử lại.";

    private final AiInterviewProperties properties;
    private final CandidateService candidateService;
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
    private final AiInterviewResponseAssembler responseAssembler;
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
            message = "AI Interview chưa được cấu hình API key.";
        } else if (!enabledByAdmin) {
            message = "Phỏng vấn AI đang bị tắt bởi quản trị viên.";
        }
        return new AiInterviewConfigResponse(
                enabled,
                message,
                properties.getQuestionCount(),
                properties.getAudioMaxSeconds(),
                properties.getAudioMaxSizeMb(),
                properties.isVoiceStreamingEnabled(),
                properties.getVoiceProvider(),
                properties.getVoiceSilenceMs(),
                properties.getVoiceConfirmationSilenceMs(),
                properties.getVoiceUnclearConfirmationDelayMs()
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
        session.setStatus("in_progress");
        session = sessionRepository.save(session);
        consumeAiSession(candidate.getUser());
        return responseAssembler.assemble(session);
    }

    @Transactional
    public AiInterviewSessionResponse createPracticeSession(AiInterviewPracticeSessionRequest request) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "session-create");
        requireAiSession(candidate.getUser());
        if (!hasBasicProfile(candidate)
                && !candidateCvRepository.existsByCandidateIdAndSourceTypeAndDeletedAtIsNull(candidate.getId(), "uploaded")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRACTICE_CONTEXT_REQUIRED", "Cần có hồ sơ cơ bản hoặc ít nhất 1 CV để luyện phỏng vấn AI");
        }
        Job job = null;
        if (request.jobId() != null && !request.jobId().isBlank()) {
            job = jobRepository.findById(parseUuid(request.jobId(), "JOB_ID_INVALID"))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Không tìm thấy việc làm"));
            if (!isActiveJob(job)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "JOB_NOT_ACTIVE", "Việc làm đã đóng hoặc hết hạn");
            }
        }
        AiQuestionSet questionSet = null;
        List<AiQuestionBank> fixedQuestions = List.of();
        boolean fixedQuestionMode = request.questionSetId() != null && !request.questionSetId().isBlank();
        if (fixedQuestionMode) {
            questionSet = questionSetRepository.findByIdAndActiveTrue(parseUuid(request.questionSetId(), "QUESTION_SET_ID_INVALID"))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QUESTION_SET_NOT_FOUND", "Không tìm thấy bộ câu hỏi"));
            fixedQuestions = questionBankRepository.findByQuestionSet_IdAndActiveTrueOrderByOrderIndexAsc(questionSet.getId());
            if (fixedQuestions.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "QUESTION_SET_EMPTY", "Bộ câu hỏi chưa có câu hỏi khả dụng");
            }
        }

        Map<String, Object> practiceContext = new LinkedHashMap<>();
        practiceContext.put("targetRole", request.targetRole().trim());
        practiceContext.put("skills", request.skills().stream().map(String::trim).filter(value -> !value.isBlank()).toList());
        practiceContext.put("jobId", job == null ? null : job.getId().toString());
        practiceContext.put("questionMode", fixedQuestionMode ? "fixed" : "ai_generated");
        practiceContext.put("questionSetId", questionSet == null ? null : questionSet.getId().toString());
        practiceContext.put("questionSetCode", questionSet == null ? null : questionSet.getCode());

        InterviewSession session = new InterviewSession();
        session.setCandidate(candidate);
        session.setJob(job);
        session.setContextType("practice");
        session.setSessionType("practice");
        session.setPracticeContext(practiceContext);
        session.setTitle("Luyện tập: " + request.targetRole().trim());
        session.setStatus("created");
        session.setStartedAt(LocalDateTime.now());
        session = sessionRepository.save(session);
        if (fixedQuestionMode) {
            copyFixedQuestions(session, fixedQuestions);
        } else {
            createNextQuestion(session);
        }
        session.setStatus("in_progress");
        session = sessionRepository.save(session);
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
            answer.setTranscriptStatus("completed");
            answer.setFeedbackStatus("pending");
            answerRepository.save(answer);
            return new AiInterviewTranscriptResponse(question.getId().toString(), transcript, "completed");
        } catch (AiProviderException exception) {
            answer.setTranscriptStatus("failed");
            answer.setErrorMessage(PROVIDER_RETRY_MESSAGE);
            answerRepository.save(answer);
            throw new ApiException(HttpStatus.BAD_GATEWAY, exception.getCode(), PROVIDER_RETRY_MESSAGE);
        }
    }

    @Transactional
    public AiInterviewSessionResponse submitAnswer(String sessionId, String questionId, String transcript) {
        return confirmAnswer(sessionId, questionId, transcript);
    }

    @Transactional
    public AiInterviewSessionResponse confirmAnswer(String sessionId, String questionId, String transcript) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "answer");
        InterviewSession session = requireMutableSession(sessionId);
        InterviewQuestion question = requireQuestion(session, questionId);
        confirmAnswer(session, question, transcript, true);
        return responseAssembler.assemble(session);
    }

    public AiInterviewSessionResponse finishInterview(String sessionId, String questionId, String transcript) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "finish");
        InterviewSession session = requireSession(sessionId);
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

        for (InterviewAnswer answer : answers) {
            if (!"completed".equals(answer.getFeedbackStatus())
                    || answer.isEvaluationFallback()
                    || !"provider".equals(answer.getEvaluationSource())
                    || answerFeedbackRepository.findByAnswerId(answer.getId()).isEmpty()) {
                evaluateAnswer(answer.getId());
            }
        }
        generateSummary(session.getId());
        return responseAssembler.assemble(requireSession(sessionId));
    }

    private void confirmAnswer(InterviewSession session,
                               InterviewQuestion question,
                               String transcript,
                               boolean advanceToNextQuestion) {
        String normalizedTranscript = transcript == null ? "" : transcript.trim();
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
        answer.setTranscriptText(normalizedTranscript);
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

    private void evaluateAnswer(UUID answerId) {
        AnswerEvaluationContext context = inTransaction(() -> {
            InterviewAnswer answer = answerRepository.findById(answerId)
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ANSWER_STALE", "Câu trả lời không còn tồn tại"));
            InterviewSession session = answer.getSession();
            prepareSessionForProvider(session);
            InterviewQuestion question = requireQuestion(session, answer.getQuestionId().toString());
            answer.setFeedbackStatus("processing");
            answer.setErrorMessage(null);
            saveAnswerClaim(answer);
            return new AnswerEvaluationContext(session, question, answer.getTranscriptText());
        });
        try {
            ShopAiKeyClient.AnswerFeedbackDraft draft = shopAiKeyClient.evaluateAnswer(
                    context.session(), context.question(), context.transcript());
            inTransaction(() -> {
                InterviewAnswer answer = answerRepository.findById(answerId)
                        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ANSWER_STALE", "Câu trả lời không còn tồn tại"));
                saveFeedback(answer, draft);
                answer.setFeedbackStatus("completed");
                answer.setEvaluationSource("provider");
                answer.setEvaluationFallback(false);
                answer.setErrorMessage(null);
                answerRepository.save(answer);
                return null;
            });
        } catch (AiProviderException exception) {
            inTransaction(() -> {
                InterviewAnswer answer = answerRepository.findById(answerId)
                        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ANSWER_STALE", "Câu trả lời không còn tồn tại"));
                answer.setFeedbackStatus("failed");
                answer.setEvaluationSource("provider");
                answer.setEvaluationFallback(false);
                answer.setErrorMessage(exception.getMessage());
                answerRepository.save(answer);
                return null;
            });
            throw providerApiException(exception, "chấm điểm câu trả lời");
        }
    }

    @Transactional
    public AiInterviewSessionResponse skipQuestion(String sessionId, String questionId) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "answer");
        InterviewSession session = requireMutableSession(sessionId);
        InterviewQuestion question = requireQuestion(session, questionId);
        InterviewAnswer answer = answerRepository.findBySessionIdAndQuestionId(session.getId(), question.getId())
                .orElseGet(() -> draftAnswer(session, question));
        if (answer.getAnsweredAt() != null) {
            if (answer.isSkipped()) {
                return responseAssembler.assemble(session);
            }
            throw new ApiException(HttpStatus.CONFLICT, "QUESTION_ALREADY_ANSWERED", "Câu hỏi này đã được chốt câu trả lời");
        }
        answer.setTranscriptText("[SKIPPED]");
        answer.setSkipped(true);
        answer.setTranscriptStatus("completed");
        answer.setFeedbackStatus("completed");
        answer.setAnsweredAt(LocalDateTime.now());
        answer.setErrorMessage(null);
        answer.setEvaluationSource("skipped");
        answer.setEvaluationFallback(false);
        saveAnswerClaim(answer);
        if (!hasOpenQuestion(session)) {
            createNextQuestion(session);
        }
        return responseAssembler.assemble(session);
    }

    public AiInterviewSessionResponse retrySummary(String sessionId) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "summary");
        InterviewSession session = requireSession(sessionId);
        if (!session.isCompleted()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INTERVIEW_NOT_FINISHED", "Chỉ tạo tổng kết sau khi buổi phỏng vấn đã kết thúc");
        }
        if (evaluableAnswers(session).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SESSION_NOT_READY_FOR_SUMMARY", "Chưa có câu trả lời để tổng kết");
        }
        generateSummary(session.getId());
        return responseAssembler.assemble(session);
    }

    public AiInterviewSessionResponse retryFeedback(String sessionId, String questionId) {
        ensureEnabled();
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "feedback");
        InterviewSession session = requireSession(sessionId);
        if (!session.isCompleted()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INTERVIEW_NOT_FINISHED", "Chỉ chấm lại sau khi buổi phỏng vấn đã kết thúc");
        }
        InterviewQuestion question = requireQuestion(session, questionId);
        InterviewAnswer answer = answerRepository.findBySessionIdAndQuestionId(session.getId(), question.getId())
                .filter(item -> item.getAnsweredAt() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "ANSWER_NOT_READY", "Chưa có câu trả lời để chấm lại"));
        if (answer.isSkipped()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SKIPPED_ANSWER", "Câu hỏi đã bỏ qua không cần chấm lại");
        }
        evaluateAnswer(answer.getId());
        generateSummary(session.getId());
        return responseAssembler.assemble(requireSession(sessionId));
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
        if (questions.size() >= properties.getQuestionCount()) {
            return;
        }
        List<InterviewAnswer> answers = answerRepository.findBySessionIdOrderByAnsweredAtAsc(session.getId());
        ShopAiKeyClient.QuestionDraft draft;
        try {
            draft = shopAiKeyClient.generateQuestion(session, session.getCandidate(), questions, answers);
        } catch (AiProviderException exception) {
            draft = fallbackQuestion(session, questions);
        }
        InterviewQuestion question = new InterviewQuestion();
        question.setSession(session);
        question.setOrderIndex(questions.size() + 1);
        question.setQuestionType(draft.questionType());
        question.setDifficulty(draft.difficulty());
        question.setSkillTag(draft.skillTag());
        question.setContent(draft.question());
        question.setTimeLimitSeconds(draft.timeLimitSeconds());
        questionRepository.save(question);
        session.setTotalQuestions(questions.size() + 1);
        sessionRepository.save(session);
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
            questionRepository.save(question);
        }
        session.setTotalQuestions(fixedQuestions.size());
        sessionRepository.save(session);
    }

    private void generateSummary(UUID sessionId) {
        SessionSummaryContext context = inTransaction(() -> {
            InterviewSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Không tìm thấy phiên phỏng vấn"));
            prepareSessionForProvider(session);
            List<InterviewQuestion> questions = questionRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);
            List<InterviewAnswer> answers = evaluableAnswers(session);
            return new SessionSummaryContext(session, questions, answers, averageScore(answers));
        });
        ShopAiKeyClient.SessionSummaryDraft draft;
        try {
            draft = shopAiKeyClient.summarizeSession(
                    context.session(), context.questions(), context.answers(), context.average());
        } catch (AiProviderException exception) {
            throw providerApiException(exception, "tạo tổng kết phỏng vấn");
        }
        inTransaction(() -> {
            InterviewSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Không tìm thấy phiên phỏng vấn"));
            AiSessionFeedback summary = sessionFeedbackRepository.findBySessionId(sessionId).orElseGet(AiSessionFeedback::new);
            summary.setSession(session);
            summary.setOverallScore(context.average());
            summary.setAiSummary(draft.summary());
            summary.setStrengths(joinLines(draft.strengths()));
            summary.setWeaknesses(joinLines(draft.weaknesses()));
            summary.setSuggestions(joinLines(draft.improvementPlan()));
            summary.setModelUsed(properties.getShopaikeyModel());
            summary.setEvaluationSource("provider");
            summary.setEvaluationFallback(false);
            sessionFeedbackRepository.save(summary);
            session.setOverallScore(context.average());
            session.setAiSummary(draft.summary());
            session.setCompletedAt(LocalDateTime.now());
            session.setStatus("completed");
            sessionRepository.save(session);
            return null;
        });
    }

    private void saveFeedback(InterviewAnswer answer, ShopAiKeyClient.AnswerFeedbackDraft draft) {
        AiAnswerFeedback feedback = answerFeedbackRepository.findByAnswerId(answer.getId()).orElseGet(AiAnswerFeedback::new);
        feedback.setAnswer(answer);
        feedback.setOverallScore(draft.score());
        feedback.setFeedback(draft.feedback());
        feedback.setStrengths(joinLines(draft.strengths()));
        feedback.setWeaknesses(joinLines(draft.weaknesses()));
        feedback.setSuggestions(joinLines(draft.suggestions()));
        feedback.setModelUsed(properties.getShopaikeyModel());
        answerFeedbackRepository.save(feedback);
    }

    private ShopAiKeyClient.QuestionDraft fallbackQuestion(InterviewSession session, List<InterviewQuestion> existingQuestions) {
        int next = existingQuestions.size() + 1;
        List<String> prompts = List.of(
                "Hãy giới thiệu ngắn gọn về kinh nghiệm của bạn và lý do bạn phù hợp với vị trí này.",
                "Hãy mô tả một dự án gần đây mà bạn tự hào nhất. Bạn đã đóng góp gì và kết quả ra sao?",
                "Khi gặp một yêu cầu khó hoặc thay đổi gấp, bạn sẽ phân tích và xử lý như thế nào?",
                "Hãy chia sẻ một lần bạn phải học công nghệ mới trong thời gian ngắn. Bạn đã học và áp dụng ra sao?",
                "Nếu được nhận vào vai trò này, 30 ngày đầu tiên bạn sẽ ưu tiên những việc gì?"
        );
        String jobTitle = session.getJob() == null ? session.getTitle() : session.getJob().getTitle();
        String baseQuestion = prompts.get(Math.min(next - 1, prompts.size() - 1));
        return new ShopAiKeyClient.QuestionDraft(
                next == 1 ? "general" : "behavioral",
                next <= 2 ? "easy" : "medium",
                jobTitle,
                baseQuestion,
                properties.getAudioMaxSeconds()
        );
    }

    private InterviewAnswer draftAnswer(InterviewSession session, InterviewQuestion question) {
        InterviewAnswer answer = new InterviewAnswer();
        answer.setSession(session);
        answer.setQuestionId(question.getId());
        answer.setTranscriptStatus("pending");
        answer.setFeedbackStatus("pending");
        return answerRepository.save(answer);
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

    private BigDecimal averageScore(List<InterviewAnswer> answers) {
        if (answers.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> scores = answers.stream()
                .filter(answer -> "provider".equals(answer.getEvaluationSource()))
                .filter(answer -> !answer.isEvaluationFallback())
                .map(answer -> answerFeedbackRepository.findByAnswerId(answer.getId())
                        .map(AiAnswerFeedback::getOverallScore)
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
        if (scores.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
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

    private record AnswerEvaluationContext(
            InterviewSession session,
            InterviewQuestion question,
            String transcript) {
    }

    private record SessionSummaryContext(
            InterviewSession session,
            List<InterviewQuestion> questions,
            List<InterviewAnswer> answers,
            BigDecimal average) {
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
