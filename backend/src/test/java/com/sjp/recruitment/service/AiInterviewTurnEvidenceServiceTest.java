package com.sjp.recruitment.service;

import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewConversationTurn;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.enums.InterviewTurnAnswerStatus;
import com.sjp.recruitment.model.enums.InterviewTurnType;
import com.sjp.recruitment.repository.InterviewAnswerRepository;
import com.sjp.recruitment.repository.InterviewConversationTurnRepository;
import com.sjp.recruitment.repository.InterviewSessionRepository;
import com.sjp.recruitment.service.ai.AiProviderException;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInterviewTurnEvidenceServiceTest {

    @Mock private InterviewSessionRepository sessionRepository;
    @Mock private InterviewConversationTurnRepository turnRepository;
    @Mock private InterviewAnswerRepository answerRepository;
    @Mock private ShopAiKeyClient shopAiKeyClient;
    @Mock private TransactionTemplate transactions;

    private AiInterviewTurnEvidenceService service;
    private InterviewSession session;
    private InterviewQuestion question;
    private InterviewConversationTurn turn;
    private InterviewAnswer answer;
    private UUID answerClientId;

    @BeforeEach
    void setUp() {
        service = new AiInterviewTurnEvidenceService(
                sessionRepository,
                turnRepository,
                answerRepository,
                shopAiKeyClient,
                transactions);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });

        session = new InterviewSession();
        session.setId(UUID.randomUUID());
        session.setEvidenceSummaryJson(Map.of());
        question = new InterviewQuestion();
        question.setId(UUID.randomUUID());
        question.setSession(session);
        question.setQuestionType("technical");
        question.setCompetencyId("problem-solving");
        question.setContent("Bạn đã xử lý lỗi backend như thế nào?");
        question.setRubric(Map.of(
                "expectedEvidence", List.of("cách phân tích", "kết quả"),
                "bars", Map.of("level1", "mơ hồ", "level3", "có quy trình", "level5", "có kiểm chứng")));

        answerClientId = UUID.randomUUID();
        turn = new InterviewConversationTurn();
        turn.setId(UUID.randomUUID());
        turn.setSession(session);
        turn.setAssessmentItem(question);
        turn.setTurnType(InterviewTurnType.CORE_QUESTION);
        turn.setAnswerStatus(InterviewTurnAnswerStatus.CONFIRMED);
        turn.setAnswerClientId(answerClientId);
        turn.setCandidateFinalAnswer("Tôi kiểm tra log, tái hiện lỗi và thêm index.");
        turn.setAnalysisJson(Map.of(
                "requestedAction", "NEXT",
                "resolvedAction", "NEXT",
                "evidenceStatus", "PENDING"));

        answer = new InterviewAnswer();
        answer.setId(UUID.randomUUID());
        answer.setSession(session);
        answer.setQuestionId(question.getId());
        answer.setEvidenceSummaryJson(Map.of("summary", "Chưa có evidence trước đó."));

        when(turnRepository.findByIdAndSessionIdForUpdate(turn.getId(), session.getId()))
                .thenReturn(Optional.of(turn));
        lenient().when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        lenient().when(answerRepository.findBySessionIdAndQuestionId(session.getId(), question.getId()))
                .thenReturn(Optional.of(answer));
        lenient().when(turnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(answerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enrichesEvidenceWithoutChangingStoredDecision() {
        ShopAiKeyClient.AnswerEvidenceDraft evidence = evidence();
        when(shopAiKeyClient.analyzeAssessmentTurnEvidence(
                any(), any(), any(), any())).thenReturn(evidence);

        service.enrich(job(answerClientId));

        assertThat(turn.getAnalysisJson())
                .containsEntry("requestedAction", "NEXT")
                .containsEntry("resolvedAction", "NEXT")
                .containsEntry("evidenceStatus", "COMPLETED")
                .containsEntry("updatedItemSummary", evidence.updatedItemSummary());
        assertThat(answer.getEvidenceSummaryJson())
                .containsEntry("summary", evidence.updatedItemSummary());
        assertThat(session.getEvidenceSummaryJson())
                .containsEntry("demonstratedCompetencies", List.of("problem-solving"));
        verify(shopAiKeyClient).analyzeAssessmentTurnEvidence(
                any(InterviewSession.class), any(InterviewQuestion.class),
                eq(turn.getCandidateFinalAnswer()), any());
        ArgumentCaptor<InterviewSession> sessionContext =
                ArgumentCaptor.forClass(InterviewSession.class);
        ArgumentCaptor<InterviewQuestion> questionContext =
                ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(shopAiKeyClient).analyzeAssessmentTurnEvidence(
                sessionContext.capture(), questionContext.capture(),
                eq(turn.getCandidateFinalAnswer()), any());
        assertThat(sessionContext.getValue()).isNotSameAs(session);
        assertThat(questionContext.getValue()).isNotSameAs(question);
        assertThat(questionContext.getValue().getRubric()).isEqualTo(question.getRubric());
    }

    @Test
    void providerFailureMarksOnlyEvidenceAsFailed() {
        when(shopAiKeyClient.analyzeAssessmentTurnEvidence(any(), any(), any(), any()))
                .thenThrow(new AiProviderException("AI_PROVIDER_TIMEOUT", "Read timed out"));

        service.enrich(job(answerClientId));

        assertThat(turn.getAnalysisJson())
                .containsEntry("resolvedAction", "NEXT")
                .containsEntry("evidenceStatus", "FAILED")
                .containsEntry("evidenceErrorCode", "AI_PROVIDER_TIMEOUT");
        assertThat(session.getEvidenceSummaryJson()).isEmpty();
    }

    @Test
    void staleAnswerClientIdentityDoesNotCallProvider() {
        service.enrich(job(UUID.randomUUID()));

        verify(shopAiKeyClient, never())
                .analyzeAssessmentTurnEvidence(any(), any(), any(), any());
        assertThat(turn.getAnalysisJson()).containsEntry("evidenceStatus", "PENDING");
    }

    private AiInterviewTurnEvidenceService.EvidenceJob job(UUID clientId) {
        return new AiInterviewTurnEvidenceService.EvidenceJob(
                session.getId(), turn.getId(), clientId);
    }

    private ShopAiKeyClient.AnswerEvidenceDraft evidence() {
        return new ShopAiKeyClient.AnswerEvidenceDraft(
                List.of("đã kiểm tra log và execution plan"),
                Map.of(
                        "accuracy", true,
                        "reasoning", true,
                        "tradeOffs", false,
                        "implementationDetail", true,
                        "realWorldApplication", true),
                List.of("chưa nêu trade-off"),
                "Ứng viên đã kiểm tra log, tái hiện lỗi và thêm index.",
                new ShopAiKeyClient.GlobalEvidenceDeltaDraft(
                        List.of("problem-solving"),
                        List.of("chưa nêu trade-off"),
                        List.of("đã dùng execution plan"),
                        List.of()));
    }
}
