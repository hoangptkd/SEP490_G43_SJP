package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.AiInterviewPracticeSessionRequest;
import com.sjp.recruitment.model.entity.AiAnswerFeedback;
import com.sjp.recruitment.model.entity.AiQuestionBank;
import com.sjp.recruitment.model.entity.AiQuestionSet;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.repository.AiAnswerFeedbackRepository;
import com.sjp.recruitment.repository.AiQuestionBankRepository;
import com.sjp.recruitment.repository.AiQuestionSetRepository;
import com.sjp.recruitment.repository.AiSessionFeedbackRepository;
import com.sjp.recruitment.repository.ApplicationRepository;
import com.sjp.recruitment.repository.CandidateCvRepository;
import com.sjp.recruitment.repository.InterviewAnswerRepository;
import com.sjp.recruitment.repository.InterviewQuestionRepository;
import com.sjp.recruitment.repository.InterviewSessionRepository;
import com.sjp.recruitment.repository.JobRepository;
import com.sjp.recruitment.service.ai.AiInterviewRateLimiter;
import com.sjp.recruitment.service.ai.GladiaTranscriptionClient;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock private ApplicationRepository applicationRepository;
    @Mock private CandidateCvRepository candidateCvRepository;
    @Mock private JobRepository jobRepository;
    @Mock private InterviewSessionRepository sessionRepository;
    @Mock private InterviewQuestionRepository questionRepository;
    @Mock private InterviewAnswerRepository answerRepository;
    @Mock private AiAnswerFeedbackRepository answerFeedbackRepository;
    @Mock private AiSessionFeedbackRepository sessionFeedbackRepository;
    @Mock private AiQuestionSetRepository questionSetRepository;
    @Mock private AiQuestionBankRepository questionBankRepository;
    @Mock private ShopAiKeyClient shopAiKeyClient;
    @Mock private GladiaTranscriptionClient gladiaTranscriptionClient;
    @Mock private JobService jobService;
    @Mock private AiInterviewRateLimiter rateLimiter;
    @Mock private AiInterviewResponseAssembler responseAssembler;
    @Mock private TransactionTemplate transactions;

    @InjectMocks private AiInterviewService service;

    private UUID candidateId;
    private InterviewSession session;
    private InterviewQuestion question;
    private InterviewAnswer answer;

    @BeforeEach
    void setUp() {
        candidateId = UUID.randomUUID();
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

        answer = new InterviewAnswer();
        answer.setId(UUID.randomUUID());
        answer.setSession(session);
        answer.setQuestionId(question.getId());

        lenient().when(properties.isEnabled()).thenReturn(true);
        lenient().when(properties.getQuestionCount()).thenReturn(1);
        lenient().when(candidateService.getCurrentCandidateProfile()).thenReturn(candidate);
        lenient().when(sessionRepository.findByIdAndCandidateIdAndDeletedAtIsNull(session.getId(), candidateId))
                .thenReturn(Optional.of(session));
        lenient().when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
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
    void creatingFixedPracticeCopiesQuestionBankWithoutGeneratingAiQuestion() {
        UUID questionSetId = UUID.randomUUID();
        AiQuestionSet questionSet = new AiQuestionSet();
        questionSet.setId(questionSetId);
        questionSet.setCode("backend_java_spring_test");
        questionSet.setTitle("Backend Java Spring Boot - Bo test");
        questionSet.setActive(true);

        AiQuestionBank first = fixedQuestion(questionSet, 1, "Hay gioi thieu kinh nghiem Java.");
        AiQuestionBank second = fixedQuestion(questionSet, 2, "Ban xu ly transaction nhu the nao?");

        when(questionSetRepository.findByIdAndActiveTrue(questionSetId)).thenReturn(Optional.of(questionSet));
        when(questionBankRepository.findByQuestionSet_IdAndActiveTrueOrderByOrderIndexAsc(questionSetId))
                .thenReturn(List.of(first, second));
        when(candidateCvRepository.existsByCandidateIdAndSourceTypeAndDeletedAtIsNull(candidateId, "uploaded")).thenReturn(true);

        service.createPracticeSession(new AiInterviewPracticeSessionRequest(
                "Backend Developer",
                List.of("Java", "Spring Boot"),
                null,
                questionSetId.toString()
        ));

        ArgumentCaptor<InterviewQuestion> questionCaptor = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionRepository, times(2)).save(questionCaptor.capture());
        List<InterviewQuestion> copiedQuestions = questionCaptor.getAllValues();
        assertEquals("Hay gioi thieu kinh nghiem Java.", copiedQuestions.get(0).getContent());
        assertEquals(1, copiedQuestions.get(0).getOrderIndex());
        assertFalse(copiedQuestions.get(0).isAiGenerated());
        assertEquals("Ban xu ly transaction nhu the nao?", copiedQuestions.get(1).getContent());
        assertEquals(2, copiedQuestions.get(1).getOrderIndex());
        assertFalse(copiedQuestions.get(1).isAiGenerated());
        verify(shopAiKeyClient, never()).generateQuestion(any(), any(), any(), any());
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
        verify(shopAiKeyClient, never()).evaluateAnswer(any(), any(), any());
        verifyNoInteractions(answerFeedbackRepository, sessionFeedbackRepository);
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
        verify(shopAiKeyClient, never()).evaluateAnswer(any(), any(), any());
    }

    @Test
    void finishingEvaluatesPendingAnswersBeforeCreatingSummary() {
        answer.setTranscriptText("Tôi xây dựng API Spring Boot và tối ưu truy vấn PostgreSQL.");
        answer.setTranscriptStatus("completed");
        answer.setFeedbackStatus("pending");
        answer.setAnsweredAt(java.time.LocalDateTime.now());

        AiAnswerFeedback persistedFeedback = new AiAnswerFeedback();
        persistedFeedback.setAnswer(answer);
        persistedFeedback.setOverallScore(BigDecimal.valueOf(82));

        when(answerRepository.findBySessionIdOrderByAnsweredAtAsc(session.getId())).thenReturn(List.of(answer));
        when(answerFeedbackRepository.findByAnswerId(answer.getId()))
                .thenReturn(Optional.empty(), Optional.of(persistedFeedback));
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId())).thenReturn(List.of(question));
        when(shopAiKeyClient.evaluateAnswer(session, question, answer.getTranscriptText())).thenReturn(
                new ShopAiKeyClient.AnswerFeedbackDraft(
                        BigDecimal.valueOf(82),
                        "Câu trả lời rõ ràng.",
                        List.of("Có ví dụ cụ thể"),
                        List.of(),
                        List.of("Bổ sung số liệu")
                )
        );
        when(shopAiKeyClient.summarizeSession(any(), any(), any(), any())).thenReturn(
                new ShopAiKeyClient.SessionSummaryDraft(
                        BigDecimal.valueOf(82),
                        "Buổi phỏng vấn tốt.",
                        List.of("Kinh nghiệm phù hợp"),
                        List.of(),
                        List.of("Tiếp tục luyện tập")
                )
        );
        when(sessionFeedbackRepository.findBySessionId(session.getId())).thenReturn(Optional.empty());

        service.finishInterview(session.getId().toString(), null, null);

        InOrder providerCalls = inOrder(shopAiKeyClient);
        providerCalls.verify(shopAiKeyClient).evaluateAnswer(session, question, answer.getTranscriptText());
        providerCalls.verify(shopAiKeyClient).summarizeSession(any(), any(), any(), any());
        assertEquals("completed", answer.getFeedbackStatus());
        assertEquals("completed", session.getStatus());
        assertEquals(BigDecimal.valueOf(82).setScale(2), session.getOverallScore());
        assertNotNull(session.getCompletedAt());
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

    private AiQuestionBank fixedQuestion(AiQuestionSet questionSet, int orderIndex, String content) {
        AiQuestionBank question = new AiQuestionBank();
        question.setId(UUID.randomUUID());
        question.setQuestionSet(questionSet);
        question.setOrderIndex(orderIndex);
        question.setQuestionType(orderIndex == 1 ? "general" : "technical");
        question.setDifficulty("medium");
        question.setSkillTag("Java");
        question.setContent(content);
        question.setTimeLimitSeconds(180);
        question.setActive(true);
        return question;
    }
}
