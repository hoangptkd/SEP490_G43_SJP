package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.AiInterviewPracticeSessionRequest;
import com.sjp.recruitment.model.dto.response.AiInterviewCvProfileResponse;
import com.sjp.recruitment.model.entity.AiAnswerFeedback;
import com.sjp.recruitment.model.entity.AiQuestionBank;
import com.sjp.recruitment.model.entity.AiQuestionSet;
import com.sjp.recruitment.model.entity.AiSessionFeedback;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.enums.AnswerAnalysisAction;
import com.sjp.recruitment.repository.AiAnswerFeedbackRepository;
import com.sjp.recruitment.repository.AiQuestionBankRepository;
import com.sjp.recruitment.repository.AiQuestionSetRepository;
import com.sjp.recruitment.repository.AiSessionFeedbackRepository;
import com.sjp.recruitment.repository.ApplicationRepository;
import com.sjp.recruitment.repository.CandidateCvRepository;
import com.sjp.recruitment.repository.InterviewAnswerRepository;
import com.sjp.recruitment.repository.InterviewAnswerCaptureRepository;
import com.sjp.recruitment.repository.InterviewQuestionRepository;
import com.sjp.recruitment.repository.InterviewConversationTurnRepository;
import com.sjp.recruitment.repository.InterviewSessionRepository;
import com.sjp.recruitment.repository.JobRepository;
import com.sjp.recruitment.service.ai.AiInterviewRateLimiter;
import com.sjp.recruitment.service.ai.AiInterviewSpeechPrefetchService;
import com.sjp.recruitment.service.ai.AiProviderException;
import com.sjp.recruitment.service.ai.GladiaTranscriptionClient;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInterviewDeferredEvaluationTest {

    @Mock private AiInterviewProperties properties;
    @Mock private CandidateService candidateService;
    @Mock private AiInterviewCvProfileService aiInterviewCvProfileService;
    @Mock private AiInterviewScoreCalculator scoreCalculator;
    @Mock private GladiaVoiceEvidenceService voiceEvidenceService;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private CandidateCvRepository candidateCvRepository;
    @Mock private JobRepository jobRepository;
    @Mock private InterviewSessionRepository sessionRepository;
    @Mock private InterviewQuestionRepository questionRepository;
    @Mock private InterviewAnswerRepository answerRepository;
    @Mock private InterviewAnswerCaptureRepository answerCaptureRepository;
    @Mock private AiAnswerFeedbackRepository answerFeedbackRepository;
    @Mock private AiSessionFeedbackRepository sessionFeedbackRepository;
    @Mock private AiQuestionSetRepository questionSetRepository;
    @Mock private AiQuestionBankRepository questionBankRepository;
    @Mock private ShopAiKeyClient shopAiKeyClient;
    @Mock private GladiaTranscriptionClient gladiaTranscriptionClient;
    @Mock private JobService jobService;
    @Mock private AiInterviewRateLimiter rateLimiter;
    @Mock private AiInterviewSpeechPrefetchService speechPrefetchService;
    @Mock private AiInterviewResponseAssembler responseAssembler;
    @Mock private AiInterviewConversationService conversationService;
    @Mock private AiInterviewConversationTemplateBank conversationTemplates;
    @Mock private AiInterviewAdaptivePrefetchCoordinator adaptivePrefetchCoordinator;
    @Mock private AiInterviewTurnEvidenceCoordinator turnEvidenceCoordinator;
    @Mock private AiInterviewTranscriptCorrectionCoordinator transcriptCorrectionCoordinator;
    @Mock private AiInterviewTranscriptReviewService transcriptReviewService;
    @Spy private AiInterviewFallbackFactory fallbackFactory = new AiInterviewFallbackFactory();
    @Mock private InterviewConversationTurnRepository conversationTurnRepository;
    @Mock private TransactionTemplate transactions;

    @InjectMocks private AiInterviewService service;

    private UUID candidateId;
    private UUID cvId;
    private InterviewSession session;
    private InterviewQuestion question;
    private InterviewAnswer answer;

    @BeforeEach
    void setUp() {
        candidateId = UUID.randomUUID();
        cvId = UUID.randomUUID();
        CandidateProfile candidate = new CandidateProfile();
        candidate.setId(candidateId);
        candidate.setFullName("Candidate Demo");

        session = new InterviewSession();
        session.setId(UUID.randomUUID());
        session.setCandidate(candidate);
        session.setStatus("in_progress");
        session.setTitle("Backend interview");

        question = new InterviewQuestion();
        question.setId(UUID.randomUUID());
        question.setSession(session);
        question.setOrderIndex(1);
        question.setContent("Hãy giới thiệu kinh nghiệm của bạn.");
        question.setCompetencyId("problem-solving");

        answer = new InterviewAnswer();
        answer.setId(UUID.randomUUID());
        answer.setSession(session);
        answer.setQuestionId(question.getId());

        lenient().when(properties.isEnabled()).thenReturn(true);
        lenient().when(properties.getQuestionCount()).thenReturn(5);
        lenient().when(properties.effectiveCoreQuestionCount()).thenReturn(5);
        lenient().when(properties.getTtsPrefetchInitialWaitMs()).thenReturn(20_000);
        lenient().when(properties.getTextAi()).thenReturn(new AiInterviewProperties.TextAi());
        lenient().when(candidateService.getCurrentCandidateProfile()).thenReturn(candidate);
        lenient().when(shopAiKeyClient.generateInitialInterviewPackage(any(), any()))
                .thenReturn(initialPackage());
        lenient().when(aiInterviewCvProfileService.analyze(cvId.toString())).thenReturn(
                new AiInterviewCvProfileResponse(
                        UUID.randomUUID().toString(), cvId.toString(), "backend-cv.pdf", "hash",
                        "Java backend profile", "junior", List.of("Java", "Spring Boot"),
                        List.of(new AiInterviewCvProfileResponse.RoleSuggestion(
                                "Backend Developer", "Phù hợp kinh nghiệm Java")),
                        List.of(new AiInterviewCvProfileResponse.EvidenceClaim(
                                "claim-1", "Dự án", "Xây dựng REST API")),
                        "cv-interview-profile-v1", true
                ));
        lenient().when(sessionRepository.findByIdAndCandidateIdAndDeletedAtIsNull(session.getId(), candidateId))
                .thenReturn(Optional.of(session));
        lenient().when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        lenient().when(sessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
        lenient().when(questionRepository.findByIdAndSessionId(question.getId(), session.getId()))
                .thenReturn(Optional.of(question));
        lenient().when(answerRepository.findById(answer.getId())).thenReturn(Optional.of(answer));
        lenient().when(answerRepository.saveAndFlush(any(InterviewAnswer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(answerRepository.save(any(InterviewAnswer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(sessionRepository.save(any(InterviewSession.class)))
                .thenAnswer(invocation -> {
                    InterviewSession saved = invocation.getArgument(0);
                    if (saved.getId() == null) {
                        saved.setId(UUID.randomUUID());
                    }
                    return saved;
                });
        lenient().when(questionRepository.save(any(InterviewQuestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(transactions.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
    }

    @Test
    void confirmingFollowUpReturnsWithoutWaitingForBackgroundEvidence() {
        when(properties.isTranscriptCorrectionEnabled()).thenReturn(true);
        UUID turnId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID answerClientId = UUID.randomUUID();
        CompletableFuture<Void> pendingEvidence = new CompletableFuture<>();
        AiInterviewConversationService.ConversationResult result =
                new AiInterviewConversationService.ConversationResult(
                        false,
                        AnswerAnalysisAction.PROBE,
                        question.getId(),
                        1,
                        false,
                        turnId,
                        answerClientId,
                        true);
        when(conversationService.confirmTurn(
                session.getId(), turnId, idempotencyKey, 1,
                "Tôi đã kiểm tra log.", "Tôi đã kiểm tra log."))
                .thenReturn(result);
        when(turnEvidenceCoordinator.submit(session.getId(), turnId, answerClientId))
                .thenReturn(pendingEvidence);

        service.confirmConversationTurn(
                session.getId().toString(),
                turnId.toString(),
                idempotencyKey.toString(),
                "Tôi đã kiểm tra log.",
                "Tôi đã kiểm tra log.",
                1);

        assertFalse(pendingEvidence.isDone());
        InOrder postDecisionOrder = inOrder(
                conversationService,
                transcriptCorrectionCoordinator,
                turnEvidenceCoordinator);
        postDecisionOrder.verify(conversationService).confirmTurn(
                session.getId(), turnId, idempotencyKey, 1,
                "Tôi đã kiểm tra log.", "Tôi đã kiểm tra log.");
        postDecisionOrder.verify(transcriptCorrectionCoordinator)
                .submitAfterDecision(session.getId(), turnId);
        postDecisionOrder.verify(turnEvidenceCoordinator)
                .submit(session.getId(), turnId, answerClientId);
        verifyNoInteractions(adaptivePrefetchCoordinator);
    }

    @Test
    void creatingPracticeUsesSelectedCvProfileAndIgnoresQuestionBankMode() {
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(any())).thenReturn(List.of());
        when(answerRepository.findBySessionIdOrderByAnsweredAtAsc(any())).thenReturn(List.of());
        service.createPracticeSession(new AiInterviewPracticeSessionRequest(
                cvId.toString(), "Backend Developer", "junior", List.of("Java", "SQL"), 7));

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(sessionRepository, times(3)).save(sessionCaptor.capture());
        InterviewSession savedSession = sessionCaptor.getValue();
        assertEquals(cvId.toString(), savedSession.getPracticeContext().get("cvId"));
        assertEquals("junior", savedSession.getPracticeContext().get("seniority"));
        assertEquals(List.of("Java", "SQL"), savedSession.getPracticeContext().get("focusSkills"));
        assertEquals("ai_generated", savedSession.getPracticeContext().get("questionMode"));
        assertEquals(7, savedSession.effectiveTargetQuestionCount());
        verify(aiInterviewCvProfileService).analyze(cvId.toString());
        verifyNoInteractions(questionSetRepository, questionBankRepository);
    }

    @Test
    void creatingPracticeRejectsUnsupportedQuestionCountBeforeLoadingCandidateOrCv() {
        ApiException exception = assertThrows(ApiException.class, () ->
                service.createPracticeSession(new AiInterviewPracticeSessionRequest(
                        cvId.toString(), "Backend Developer", "junior", List.of("Java"), 6)));

        assertEquals("QUESTION_COUNT_INVALID", exception.getCode());
        verifyNoInteractions(candidateService, aiInterviewCvProfileService);
    }

    @Test
    void creatingPracticeWaitsForExactOpeningAndFirstQuestionSpeechCache() {
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(any())).thenReturn(List.of());
        when(answerRepository.findBySessionIdOrderByAnsweredAtAsc(any())).thenReturn(List.of());
        when(responseAssembler.currentConversationSpeech(any()))
                .thenReturn("Chào bạn.\nBạn hãy giới thiệu kinh nghiệm Java.");
        when(conversationTemplates.initialPriorityPhrases(List.of()))
                .thenReturn(List.of("Được rồi."));

        service.createPracticeSession(new AiInterviewPracticeSessionRequest(
                cvId.toString(), "Backend Developer", "junior", List.of("Java")));

        verify(speechPrefetchService).prefetchSegmentsAndAwait(
                "Chào bạn.\nBạn hãy giới thiệu kinh nghiệm Java.",
                20_000,
                List.of("opening", "initial_question_1"));
        verify(conversationTemplates).initialPriorityPhrases(List.of());
        verify(speechPrefetchService).prefetchLabeled(List.of(
                new AiInterviewSpeechPrefetchService.PrefetchRequest("Được rồi.", null)
        ));
    }

    @Test
    void replayQuestionAtomicallyIncrementsOnlyTheCurrentOpenQuestion() {
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(question));
        when(answerRepository.findBySessionIdAndQuestionId(session.getId(), question.getId()))
                .thenReturn(Optional.empty());
        when(questionRepository.incrementReplayCount(question.getId(), session.getId())).thenReturn(1);

        service.replayQuestion(session.getId().toString(), question.getId().toString());

        verify(questionRepository).incrementReplayCount(question.getId(), session.getId());
    }

    @Test
    void aiQuestionGenerationFailureDoesNotCreateFallbackQuestions() {
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(any())).thenReturn(List.of());
        when(answerRepository.findBySessionIdOrderByAnsweredAtAsc(any())).thenReturn(List.of());
        when(shopAiKeyClient.generateInitialInterviewPackage(any(), any()))
                .thenThrow(new AiProviderException("AI_PROVIDER_FAILED", "Provider unavailable"));

        assertThrows(AiProviderException.class, () -> service.createPracticeSession(
                new AiInterviewPracticeSessionRequest(
                        cvId.toString(), "Backend Developer", "junior", List.of("Java")
                )
        ));

        verify(questionRepository, never()).save(any(InterviewQuestion.class));
        verify(conversationService, never()).initialize(any(InterviewSession.class));
        verify(responseAssembler, never()).assemble(any(InterviewSession.class));
    }

    @Test
    void generatedQuestionStoresExplicitAiProvenance() {
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(any())).thenReturn(List.of());
        when(answerRepository.findBySessionIdOrderByAnsweredAtAsc(any())).thenReturn(List.of());
        service.createPracticeSession(new AiInterviewPracticeSessionRequest(
                cvId.toString(), "Backend Developer", "junior", List.of("Java")
        ));

        ArgumentCaptor<InterviewQuestion> questionCaptor = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionRepository, times(3)).save(questionCaptor.capture());
        InterviewQuestion generatedQuestion = questionCaptor.getAllValues().get(0);
        assertEquals("AI_GENERATED", generatedQuestion.getSourceType());
        assertEquals("ai-question-initial-v2", generatedQuestion.getPromptVersion());
        assertEquals("bars-v2", generatedQuestion.getRubricVersion());
        assertEquals("problem-solving", generatedQuestion.getCompetencyId());
        assertEquals(null, generatedQuestion.getSourceId());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> competencies = (List<Map<String, Object>>) generatedQuestion.getSession().getEvaluationProfile()
                .get("competencies");
        assertEquals(List.of("problem-solving", "technical-depth", "communication"),
                generatedQuestion.getSession().getEvaluationProfile().get("scoredCompetencyIds"));
        assertEquals(0.0, competencies.stream()
                .filter(item -> "leadership".equals(item.get("id")))
                .findFirst().orElseThrow().get("scoredWeight"));
        double corePriority = ((Number) competencies.stream()
                .filter(item -> "problem-solving".equals(item.get("id")))
                .findFirst().orElseThrow().get("priority")).doubleValue();
        double trainablePriority = ((Number) competencies.stream()
                .filter(item -> "leadership".equals(item.get("id")))
                .findFirst().orElseThrow().get("priority")).doubleValue();
        assertEquals(4.8, corePriority, 0.0001);
        assertEquals(1.8, trainablePriority, 0.0001);
        assertTrue(corePriority > trainablePriority);
        assertFalse(competencies.get(0).containsKey("neededAtEntry"));
    }

    @Test
    void confirmingThirdAnswerCreatesExactlyTwoAdaptiveQuestions() {
        InterviewQuestion first = interviewQuestion(1, "problem-solving", "Câu 1");
        InterviewQuestion second = interviewQuestion(2, "technical-depth", "Câu 2");
        InterviewQuestion third = interviewQuestion(3, "communication", "Câu 3");
        InterviewAnswer firstAnswer = completedAnswer(first, "Trả lời 1");
        InterviewAnswer secondAnswer = completedAnswer(second, "Trả lời 2");
        answer.setQuestionId(third.getId());
        answer.setSession(session);
        session.setEvaluationProfile(scoredProfile());
        when(questionRepository.findByIdAndSessionId(third.getId(), session.getId())).thenReturn(Optional.of(third));
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(first, second, third));
        when(answerRepository.findBySessionIdAndQuestionId(eq(session.getId()), any())).thenAnswer(invocation -> {
            UUID questionId = invocation.getArgument(1);
            if (questionId.equals(first.getId())) return Optional.of(firstAnswer);
            if (questionId.equals(second.getId())) return Optional.of(secondAnswer);
            return Optional.of(answer);
        });
        when(answerRepository.findBySessionIdOrderByAnsweredAtAsc(session.getId()))
                .thenReturn(List.of(firstAnswer, secondAnswer, answer));
        when(shopAiKeyClient.generateAdaptiveInterviewQuestions(
                session, List.of(first, second, third), List.of(firstAnswer, secondAnswer, answer), 2))
                .thenReturn(List.of(
                        rubricQuestion("problem-solving", "Câu đào sâu 4"),
                        rubricQuestion("technical-depth", "Câu bổ sung 5")
                ));

        service.confirmAnswer(session.getId().toString(), third.getId().toString(), "Trả lời 3");

        ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionRepository, times(2)).save(captor.capture());
        assertEquals(List.of(4, 5), captor.getAllValues().stream().map(InterviewQuestion::getOrderIndex).toList());
        assertEquals("ai-question-adaptive-v3", captor.getAllValues().get(0).getPromptVersion());
        assertEquals("bars-v2", captor.getAllValues().get(1).getRubricVersion());
        verify(shopAiKeyClient, times(1)).generateAdaptiveInterviewQuestions(any(), any(), any(), eq(2));
    }

    @Test
    void confirmingThirdAnswerEndsCoreQuestionGenerationWhenTargetIsThree() {
        InterviewQuestion first = interviewQuestion(1, "problem-solving", "Câu 1");
        InterviewQuestion second = interviewQuestion(2, "technical-depth", "Câu 2");
        InterviewQuestion third = interviewQuestion(3, "communication", "Câu 3");
        InterviewAnswer firstAnswer = completedAnswer(first, "Trả lời 1");
        InterviewAnswer secondAnswer = completedAnswer(second, "Trả lời 2");
        session.setTargetQuestionCount(3);
        answer.setQuestionId(third.getId());
        answer.setSession(session);
        when(questionRepository.findByIdAndSessionId(third.getId(), session.getId()))
                .thenReturn(Optional.of(third));
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(first, second, third));
        when(answerRepository.findBySessionIdAndQuestionId(eq(session.getId()), any())).thenAnswer(invocation -> {
            UUID questionId = invocation.getArgument(1);
            if (questionId.equals(first.getId())) return Optional.of(firstAnswer);
            if (questionId.equals(second.getId())) return Optional.of(secondAnswer);
            return Optional.of(answer);
        });

        service.confirmAnswer(session.getId().toString(), third.getId().toString(), "Trả lời 3");

        verify(shopAiKeyClient, never())
                .generateAdaptiveInterviewQuestions(any(), any(), any(), any(Integer.class));
        verify(questionRepository, never()).save(any(InterviewQuestion.class));
    }

    @Test
    void confirmingNinthAnswerCreatesOnlyOneFinalQuestionWhenTargetIsTen() {
        session.setTargetQuestionCount(10);
        session.setEvaluationProfile(scoredProfile());
        List<String> competencyIds = List.of(
                "problem-solving", "technical-depth", "communication");
        List<InterviewQuestion> questions = new ArrayList<>();
        List<InterviewAnswer> answers = new ArrayList<>();
        for (int order = 1; order <= 9; order++) {
            InterviewQuestion item = interviewQuestion(
                    order, competencyIds.get((order - 1) % competencyIds.size()), "Câu " + order);
            questions.add(item);
            if (order < 9) {
                answers.add(completedAnswer(item, "Trả lời " + order));
            } else {
                answer.setQuestionId(item.getId());
                answer.setSession(session);
                answers.add(answer);
            }
        }
        InterviewQuestion ninth = questions.get(8);
        when(questionRepository.findByIdAndSessionId(ninth.getId(), session.getId()))
                .thenReturn(Optional.of(ninth));
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(questions);
        when(answerRepository.findBySessionIdAndQuestionId(eq(session.getId()), any()))
                .thenAnswer(invocation -> answers.stream()
                        .filter(item -> item.getQuestionId().equals(invocation.getArgument(1)))
                        .findFirst());
        when(answerRepository.findBySessionIdOrderByAnsweredAtAsc(session.getId()))
                .thenReturn(answers);
        when(shopAiKeyClient.generateAdaptiveInterviewQuestions(session, questions, answers, 1))
                .thenReturn(List.of(rubricQuestion("problem-solving", "Câu cuối 10")));

        service.confirmAnswer(session.getId().toString(), ninth.getId().toString(), "Trả lời 9");

        ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionRepository).save(captor.capture());
        assertEquals(10, captor.getValue().getOrderIndex());
        verify(shopAiKeyClient).generateAdaptiveInterviewQuestions(session, questions, answers, 1);
    }

    @Test
    void adaptiveBatchAutomaticallyRetriesOnceWhenFirstResultMissesFourthScoredCompetency() {
        InterviewQuestion first = interviewQuestion(1, "problem-solving", "Câu 1");
        InterviewQuestion second = interviewQuestion(2, "technical-depth", "Câu 2");
        InterviewQuestion third = interviewQuestion(3, "communication", "Câu 3");
        InterviewAnswer firstAnswer = completedAnswer(first, "Trả lời 1");
        InterviewAnswer secondAnswer = completedAnswer(second, "Trả lời 2");
        answer.setQuestionId(third.getId());
        answer.setSession(session);
        session.setEvaluationProfile(scoredProfileFour());
        when(questionRepository.findByIdAndSessionId(third.getId(), session.getId())).thenReturn(Optional.of(third));
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(first, second, third));
        when(answerRepository.findBySessionIdAndQuestionId(eq(session.getId()), any())).thenAnswer(invocation -> {
            UUID questionId = invocation.getArgument(1);
            if (questionId.equals(first.getId())) return Optional.of(firstAnswer);
            if (questionId.equals(second.getId())) return Optional.of(secondAnswer);
            return Optional.of(answer);
        });
        when(answerRepository.findBySessionIdOrderByAnsweredAtAsc(session.getId()))
                .thenReturn(List.of(firstAnswer, secondAnswer, answer));
        when(shopAiKeyClient.generateAdaptiveInterviewQuestions(any(), any(), any(), eq(2)))
                .thenReturn(List.of(
                        rubricQuestion("problem-solving", "Đào sâu câu 4"),
                        rubricQuestion("technical-depth", "Cross-check câu 5")
                ));
        when(shopAiKeyClient.regenerateAdaptiveInterviewQuestionsForCoverage(any(), any(), any(), eq(2)))
                .thenReturn(List.of(
                        rubricQuestion("leadership", "Bổ sung năng lực thứ tư"),
                        rubricQuestion("problem-solving", "Đào sâu câu 5")
                ));

        service.confirmAnswer(session.getId().toString(), third.getId().toString(), "Trả lời 3");

        ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionRepository, times(2)).save(captor.capture());
        assertEquals("leadership", captor.getAllValues().get(0).getCompetencyId());
        verify(shopAiKeyClient).generateAdaptiveInterviewQuestions(any(), any(), any(), eq(2));
        verify(shopAiKeyClient).regenerateAdaptiveInterviewQuestionsForCoverage(any(), any(), any(), eq(2));
    }

    @Test
    void adaptiveGenerationFailureCreatesFallbackQuestionsAndContinues() {
        InterviewQuestion first = interviewQuestion(1, "problem-solving", "Câu 1");
        InterviewQuestion second = interviewQuestion(2, "technical-depth", "Câu 2");
        InterviewQuestion third = interviewQuestion(3, "communication", "Câu 3");
        InterviewAnswer firstAnswer = completedAnswer(first, "Trả lời 1");
        InterviewAnswer secondAnswer = completedAnswer(second, "Trả lời 2");
        answer.setQuestionId(third.getId());
        answer.setSession(session);
        session.setEvaluationProfile(scoredProfile());
        when(questionRepository.findByIdAndSessionId(third.getId(), session.getId())).thenReturn(Optional.of(third));
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(first, second, third));
        when(answerRepository.findBySessionIdAndQuestionId(eq(session.getId()), any())).thenAnswer(invocation -> {
            UUID questionId = invocation.getArgument(1);
            if (questionId.equals(first.getId())) return Optional.of(firstAnswer);
            if (questionId.equals(second.getId())) return Optional.of(secondAnswer);
            return Optional.of(answer);
        });
        when(answerRepository.findBySessionIdOrderByAnsweredAtAsc(session.getId()))
                .thenReturn(List.of(firstAnswer, secondAnswer, answer));
        when(shopAiKeyClient.generateAdaptiveInterviewQuestions(any(), any(), any(), eq(2)))
                .thenThrow(new AiProviderException("AI_PROVIDER_FAILED", "provider unavailable"));

        service.confirmAnswer(session.getId().toString(), third.getId().toString(), "Trả lời 3");

        assertEquals("Trả lời 3", answer.getTranscriptText());
        assertNotNull(answer.getAnsweredAt());
        ArgumentCaptor<InterviewQuestion> questionCaptor = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionRepository, times(2)).save(questionCaptor.capture());
        assertTrue(questionCaptor.getAllValues().stream()
                .allMatch(item -> AiInterviewFallbackFactory.FALLBACK_PROMPT_VERSION
                        .equals(item.getPromptVersion())));
        verify(shopAiKeyClient).generateAdaptiveInterviewQuestions(any(), any(), any(), eq(2));
    }

    @Test
    void confirmingAnswerPersistsTranscriptWithoutEvaluation() {
        when(answerRepository.findBySessionIdAndQuestionId(session.getId(), question.getId()))
                .thenReturn(Optional.of(answer));
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(question));

        service.confirmAnswer(session.getId().toString(), question.getId().toString(), "Tôi có ba năm kinh nghiệm Java.");

        assertEquals("Tôi có ba năm kinh nghiệm Java.", answer.getTranscriptText());
        assertEquals("completed", answer.getTranscriptStatus());
        assertEquals("pending", answer.getFeedbackStatus());
        assertNotNull(answer.getAnsweredAt());
        assertFalse(answer.isSkipped());
        verify(shopAiKeyClient, never()).evaluateInterview(any(), any(), any());
        verifyNoInteractions(answerFeedbackRepository, sessionFeedbackRepository);
    }

    @Test
    void confirmingEditedDraftKeepsRawTranscriptAndScoresOnlyFinalTranscript() {
        when(answerRepository.findBySessionIdAndQuestionId(session.getId(), question.getId()))
                .thenReturn(Optional.of(answer));
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(question));
        answer.setRawTranscript("Em dùng spring bút để xây dựng API.");
        answer.setTranscriptText(answer.getRawTranscript());
        answer.setConversationState("REVIEWING_TRANSCRIPT");

        service.confirmAnswer(
                session.getId().toString(),
                question.getId().toString(),
                "Em dùng spring bút để xây dựng API.",
                "Em dùng Spring Boot để xây dựng API."
        );

        assertEquals("Em dùng spring bút để xây dựng API.", answer.getRawTranscript());
        assertEquals("Em dùng Spring Boot để xây dựng API.", answer.getFinalTranscript());
        assertEquals(answer.getFinalTranscript(), answer.getTranscriptText());
        assertTrue(answer.isTranscriptEdited());
        assertEquals(1, answer.getTranscriptEditCount());
        assertEquals("ANSWER_CONFIRMED", answer.getConversationState());
    }

    @Test
    void confirmingAnswerIsIdempotentButRejectsConflictingReplay() {
        answer.setTranscriptText("Câu trả lời đã chốt");
        answer.setTranscriptStatus("completed");
        answer.setFeedbackStatus("pending");
        answer.setAnsweredAt(java.time.LocalDateTime.now());
        when(answerRepository.findBySessionIdAndQuestionId(session.getId(), question.getId()))
                .thenReturn(Optional.of(answer));

        service.confirmAnswer(session.getId().toString(), question.getId().toString(), "Câu trả lời đã chốt");

        assertThrows(ApiException.class, () -> service.confirmAnswer(
                session.getId().toString(),
                question.getId().toString(),
                "Nội dung khác"
        ));
        verify(shopAiKeyClient, never()).evaluateInterview(any(), any(), any());
    }

    @Test
    void finishingEvaluatesWholeInterviewInOneProviderCall() {
        answer.setTranscriptText("Tôi xây dựng API Spring Boot và tối ưu truy vấn PostgreSQL.");
        answer.setTranscriptStatus("completed");
        answer.setFeedbackStatus("pending");
        answer.setAnsweredAt(java.time.LocalDateTime.now());
        InterviewQuestion skippedQuestion = interviewQuestion(2, "technical-depth", "Câu đã bỏ qua");
        InterviewAnswer skippedAnswer = completedAnswer(skippedQuestion, "[SKIPPED]");
        skippedAnswer.setSkipped(true);
        skippedAnswer.setFeedbackStatus("completed");

        when(answerRepository.findBySessionIdOrderByAnsweredAtAsc(session.getId()))
                .thenReturn(List.of(answer, skippedAnswer));
        when(answerFeedbackRepository.findByAnswerId(answer.getId())).thenReturn(Optional.empty());
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(question, skippedQuestion));
        ShopAiKeyClient.QuestionRatingDraft rating = new ShopAiKeyClient.QuestionRatingDraft(
                question.getId().toString(), "problem-solving", 4,
                List.of("Có ví dụ cụ thể"), List.of("Bổ sung số liệu"));
        when(shopAiKeyClient.evaluateInterview(
                session, List.of(question, skippedQuestion), List.of(answer))).thenReturn(
                new ShopAiKeyClient.InterviewEvaluationDraft(
                        List.of(rating), "Buổi phỏng vấn tốt.",
                        List.of("Kinh nghiệm phù hợp"),
                        List.of("Cần bổ sung số liệu"),
                        List.of("Trong 2 tuần, luyện 3 câu trả lời có số liệu mỗi ngày")));
        when(scoreCalculator.calculate(any(), any(), any(), any())).thenReturn(
                new AiInterviewScoreCalculator.ScoreResult(
                        BigDecimal.valueOf(82), BigDecimal.valueOf(70), BigDecimal.valueOf(72),
                        BigDecimal.valueOf(0.15), 1, BigDecimal.valueOf(2), 1, 0,
                        BigDecimal.valueOf(80.20), Map.of(
                                question.getId(), new AiInterviewScoreCalculator.QuestionScoreResult(
                                        "RATED", 4, BigDecimal.valueOf(75), null),
                                skippedQuestion.getId(), new AiInterviewScoreCalculator.QuestionScoreResult(
                                        "NOT_ANSWERED", null, BigDecimal.ZERO, "SKIPPED")
                        ), Map.of()));
        when(sessionFeedbackRepository.findBySessionId(session.getId())).thenReturn(Optional.empty());

        service.finishInterview(session.getId().toString(), null, null);

        verify(shopAiKeyClient).evaluateInterview(
                session, List.of(question, skippedQuestion), List.of(answer));
        verify(voiceEvidenceService).awaitAndLoad(List.of(answer.getId(), skippedAnswer.getId()));
        ArgumentCaptor<AiAnswerFeedback> feedbackCaptor = ArgumentCaptor.forClass(AiAnswerFeedback.class);
        verify(answerFeedbackRepository, times(2)).save(feedbackCaptor.capture());
        AiAnswerFeedback skippedFeedback = feedbackCaptor.getAllValues().stream()
                .filter(item -> item.getAnswer().isSkipped())
                .findFirst().orElseThrow();
        assertEquals("NOT_ANSWERED", skippedFeedback.getEvaluationStatus());
        assertEquals(null, skippedFeedback.getBarsLevel());
        assertEquals(BigDecimal.ZERO, skippedFeedback.getOverallScore());
        assertEquals("SKIPPED", skippedFeedback.getScoreReason());
        assertEquals("completed", answer.getFeedbackStatus());
        assertEquals("completed", skippedAnswer.getFeedbackStatus());
        assertEquals("completed", session.getStatus());
        assertEquals(BigDecimal.valueOf(80.20), session.getOverallScore());
        assertNotNull(session.getCompletedAt());
        ArgumentCaptor<AiSessionFeedback> summaryCaptor = ArgumentCaptor.forClass(AiSessionFeedback.class);
        verify(sessionFeedbackRepository).save(summaryCaptor.capture());
        assertEquals("Cần bổ sung số liệu", summaryCaptor.getValue().getWeaknesses());
        assertEquals("Trong 2 tuần, luyện 3 câu trả lời có số liệu mỗi ngày",
                summaryCaptor.getValue().getSuggestions());
    }

    @Test
    void finishingReloadsCurrentAnswerBeforePersistingEvaluation() {
        answer.setVersion(1L);
        answer.setTranscriptText("Tôi kiểm tra log và đo lại hiệu năng.");
        answer.setTranscriptStatus("completed");
        answer.setFeedbackStatus("pending");
        answer.setAnsweredAt(LocalDateTime.now());

        InterviewAnswer currentAnswer = new InterviewAnswer();
        currentAnswer.setId(answer.getId());
        currentAnswer.setVersion(2L);
        currentAnswer.setSession(session);
        currentAnswer.setQuestionId(question.getId());
        currentAnswer.setTranscriptText(answer.getTranscriptText());
        currentAnswer.setTranscriptStatus("completed");
        currentAnswer.setFeedbackStatus("pending");
        currentAnswer.setAnsweredAt(answer.getAnsweredAt());

        when(answerRepository.findBySessionIdOrderByAnsweredAtAsc(session.getId()))
                .thenReturn(List.of(answer), List.of(answer), List.of(currentAnswer));
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(question));
        when(answerFeedbackRepository.findByAnswerId(currentAnswer.getId()))
                .thenReturn(Optional.empty());
        ShopAiKeyClient.QuestionRatingDraft rating = new ShopAiKeyClient.QuestionRatingDraft(
                question.getId().toString(), "problem-solving", 3,
                List.of("Có quy trình"), List.of("Cần thêm số liệu"));
        when(shopAiKeyClient.evaluateInterview(session, List.of(question), List.of(answer)))
                .thenReturn(new ShopAiKeyClient.InterviewEvaluationDraft(
                        List.of(rating), "Đã hoàn thành.", List.of("Có quy trình"),
                        List.of("Cần thêm số liệu"), List.of("Luyện lại với số liệu")));
        when(scoreCalculator.calculate(any(), any(), any(), any())).thenReturn(
                new AiInterviewScoreCalculator.ScoreResult(
                        BigDecimal.valueOf(50), null, null,
                        BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, 1,
                        BigDecimal.valueOf(50), Map.of(
                                question.getId(), new AiInterviewScoreCalculator.QuestionScoreResult(
                                        "RATED", 3, BigDecimal.valueOf(50), null)
                        ), Map.of()));
        when(sessionFeedbackRepository.findBySessionId(session.getId())).thenReturn(Optional.empty());

        service.finishInterview(session.getId().toString(), null, null);

        assertEquals("pending", answer.getFeedbackStatus());
        assertEquals("completed", currentAnswer.getFeedbackStatus());
        verify(answerRepository).save(currentAnswer);
        ArgumentCaptor<AiAnswerFeedback> feedbackCaptor = ArgumentCaptor.forClass(AiAnswerFeedback.class);
        verify(answerFeedbackRepository).save(feedbackCaptor.capture());
        assertSame(currentAnswer, feedbackCaptor.getValue().getAnswer());
    }

    @Test
    void finalEvaluationProviderFailureCompletesSessionWithFallbackResult() {
        answer.setTranscriptText("Tôi kiểm tra log, xác định truy vấn chậm, thêm index và đo lại thời gian phản hồi.");
        answer.setFinalTranscript(answer.getTranscriptText());
        answer.setTranscriptStatus("completed");
        answer.setFeedbackStatus("pending");
        answer.setAnsweredAt(LocalDateTime.now());
        session.setEvaluationProfile(scoredProfile());

        when(answerRepository.findBySessionIdOrderByAnsweredAtAsc(session.getId()))
                .thenReturn(List.of(answer));
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(question));
        when(shopAiKeyClient.evaluateInterview(session, List.of(question), List.of(answer)))
                .thenThrow(new AiProviderException("AI_PROVIDER_HTTP_ERROR", "401 Unauthorized"));
        when(scoreCalculator.calculate(any(), any(), any(), any())).thenReturn(
                new AiInterviewScoreCalculator.ScoreResult(
                        BigDecimal.valueOf(50), null, null,
                        BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, 1,
                        BigDecimal.valueOf(50), Map.of(
                                question.getId(), new AiInterviewScoreCalculator.QuestionScoreResult(
                                        "RATED", 3, BigDecimal.valueOf(50), null)
                        ), Map.of()));
        when(sessionFeedbackRepository.findBySessionId(session.getId())).thenReturn(Optional.empty());

        service.finishInterview(session.getId().toString(), null, null);

        assertEquals("completed", session.getStatus());
        assertEquals("completed", answer.getFeedbackStatus());
        assertEquals("fallback", answer.getEvaluationSource());
        assertTrue(answer.isEvaluationFallback());
        assertEquals(null, session.getLastErrorCode());

        ArgumentCaptor<AiSessionFeedback> summaryCaptor = ArgumentCaptor.forClass(AiSessionFeedback.class);
        verify(sessionFeedbackRepository).save(summaryCaptor.capture());
        AiSessionFeedback summary = summaryCaptor.getValue();
        assertEquals("fallback", summary.getEvaluationSource());
        assertTrue(summary.isEvaluationFallback());
        assertTrue(summary.getAiSummary().contains("hoàn thành buổi luyện phỏng vấn"));
    }

    @Test
    void finishingCompletedSessionIsIdempotent() {
        session.setStatus("completed");

        service.finishInterview(session.getId().toString(), null, null);

        verifyNoInteractions(shopAiKeyClient);
        verify(answerRepository, never()).findBySessionIdOrderByAnsweredAtAsc(any());
    }

    @Test
    void finishingWithoutAnyAnswerIsRejected() {
        when(answerRepository.findBySessionIdOrderByAnsweredAtAsc(session.getId())).thenReturn(List.of());

        assertThrows(ApiException.class, () -> service.finishInterview(session.getId().toString(), null, null));

        verifyNoInteractions(shopAiKeyClient);
    }

    private ShopAiKeyClient.InterviewPackageDraft initialPackage() {
        ShopAiKeyClient.EvaluationProfileDraft profile = new ShopAiKeyClient.EvaluationProfileDraft(
                "Backend Developer",
                "junior",
                2,
                List.of("problem-solving", "technical-depth", "communication"),
                List.of(
                        new ShopAiKeyClient.CompetencyDraft(
                                "problem-solving", "Giải quyết vấn đề", "Phân tích và xử lý vấn đề",
                                5, 4, 5, "content", "Năng lực cốt lõi"),
                        new ShopAiKeyClient.CompetencyDraft(
                                "technical-depth", "Kỹ thuật", "Vận dụng kiến thức kỹ thuật",
                                5, 4, 4, "content", "Cần cho vai trò"),
                        new ShopAiKeyClient.CompetencyDraft(
                                "communication", "Giao tiếp", "Trình bày rõ ràng",
                                3, 3, 3, "both", "Hỗ trợ phối hợp"),
                        new ShopAiKeyClient.CompetencyDraft(
                                "leadership", "Dẫn dắt", "Hỗ trợ định hướng nhóm",
                                2, 1, 2, "content", "Có thể phát triển sau")
                )
        );
        List<ShopAiKeyClient.RubricQuestionDraft> questions = List.of(
                rubricQuestion("problem-solving", "Hãy mô tả một vấn đề bạn đã xử lý."),
                rubricQuestion("technical-depth", "Bạn thiết kế REST API như thế nào?"),
                rubricQuestion("communication", "Hãy kể về một lần phối hợp trong nhóm.")
        );
        return new ShopAiKeyClient.InterviewPackageDraft(profile, questions);
    }

    private ShopAiKeyClient.RubricQuestionDraft rubricQuestion(String competencyId, String content) {
        return new ShopAiKeyClient.RubricQuestionDraft(
                "behavioral", "medium", competencyId, null, content, 180,
                List.of("Hành động cá nhân", "Kết quả"),
                "Không có bằng chứng", "Có bằng chứng đạt yêu cầu", "Bằng chứng mạnh và cụ thể"
        );
    }

    private InterviewQuestion interviewQuestion(int orderIndex, String competencyId, String content) {
        InterviewQuestion item = new InterviewQuestion();
        item.setId(UUID.randomUUID());
        item.setSession(session);
        item.setOrderIndex(orderIndex);
        item.setCompetencyId(competencyId);
        item.setContent(content);
        return item;
    }

    private InterviewAnswer completedAnswer(InterviewQuestion item, String transcript) {
        InterviewAnswer completed = new InterviewAnswer();
        completed.setId(UUID.randomUUID());
        completed.setSession(session);
        completed.setQuestionId(item.getId());
        completed.setTranscriptText(transcript);
        completed.setAnsweredAt(LocalDateTime.now());
        return completed;
    }

    private Map<String, Object> scoredProfile() {
        return Map.of(
                "scoredCompetencyIds", List.of("problem-solving", "technical-depth", "communication"),
                "competencies", List.of(
                        Map.of("id", "problem-solving"),
                        Map.of("id", "technical-depth"),
                        Map.of("id", "communication")
                )
        );
    }

    private Map<String, Object> scoredProfileFour() {
        return Map.of(
                "scoredCompetencyIds", List.of(
                        "problem-solving", "technical-depth", "communication", "leadership"),
                "competencies", List.of(
                        Map.of("id", "problem-solving"),
                        Map.of("id", "technical-depth"),
                        Map.of("id", "communication"),
                        Map.of("id", "leadership")
                )
        );
    }

}
