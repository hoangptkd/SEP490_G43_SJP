package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewAnswerCapture;
import com.sjp.recruitment.model.entity.InterviewConversationTurn;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.enums.AnswerAnalysisAction;
import com.sjp.recruitment.model.enums.InterviewDialogueState;
import com.sjp.recruitment.model.enums.InterviewTurnAnswerStatus;
import com.sjp.recruitment.model.enums.InterviewTurnType;
import com.sjp.recruitment.model.enums.TranscriptCorrectionStatus;
import com.sjp.recruitment.repository.InterviewAnswerRepository;
import com.sjp.recruitment.repository.InterviewAnswerCaptureRepository;
import com.sjp.recruitment.repository.InterviewConversationTurnRepository;
import com.sjp.recruitment.repository.InterviewQuestionRepository;
import com.sjp.recruitment.repository.InterviewSessionRepository;
import com.sjp.recruitment.service.ai.AiProviderException;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiInterviewConversationServiceTest {

    @Mock private CandidateService candidateService;
    @Mock private InterviewSessionRepository sessionRepository;
    @Mock private InterviewQuestionRepository questionRepository;
    @Mock private InterviewAnswerRepository answerRepository;
    @Mock private InterviewAnswerCaptureRepository captureRepository;
    @Mock private InterviewConversationTurnRepository turnRepository;
    @Mock private ShopAiKeyClient shopAiKeyClient;
    @Mock private TransactionTemplate transactions;

    private AiInterviewConversationService service;
    private CandidateProfile candidate;
    private InterviewSession session;
    private InterviewQuestion question;
    private InterviewConversationTurn coreTurn;
    private InterviewAnswer aggregate;

    @BeforeEach
    void setUp() {
        AiInterviewProperties properties = new AiInterviewProperties();
        properties.setMaxProbesPerCore(1);
        properties.setMaxClarifiesPerCore(1);
        properties.setMaxTotalAssessmentTurns(10);
        service = new AiInterviewConversationService(
                properties,
                candidateService,
                sessionRepository,
                questionRepository,
                answerRepository,
                captureRepository,
                turnRepository,
                shopAiKeyClient,
                new AiInterviewFallbackFactory(),
                new AiInterviewConversationPolicy(properties),
                new AiInterviewConversationTemplateBank(),
                transactions
        );

        candidate = new CandidateProfile();
        candidate.setId(UUID.randomUUID());
        when(candidateService.getCurrentCandidateProfile()).thenReturn(candidate);

        session = new InterviewSession();
        session.setId(UUID.randomUUID());
        session.setCandidate(candidate);
        session.setStatus("in_progress");
        session.setDialogueState(InterviewDialogueState.WAITING_ANSWER);
        session.setDialogueVersion(1);
        session.setNextTurnSequence(3);
        session.setAssessmentTurnCount(1);
        session.setEvidenceSummaryJson(Map.of());

        question = new InterviewQuestion();
        question.setId(UUID.randomUUID());
        question.setSession(session);
        question.setOrderIndex(1);
        question.setQuestionType("behavioral");
        question.setCompetencyId("teamwork");
        question.setContent("Hãy kể về một lần bạn giải quyết bất đồng trong nhóm?");
        question.setRubric(Map.of(
                "expectedEvidence", List.of("hành động cá nhân", "kết quả"),
                "bars", Map.of("level1", "mơ hồ", "level3", "có hành động", "level5", "có kết quả")
        ));

        coreTurn = new InterviewConversationTurn();
        coreTurn.setId(UUID.randomUUID());
        coreTurn.setSession(session);
        coreTurn.setAssessmentItem(question);
        coreTurn.setSequenceNo(2);
        coreTurn.setTurnType(InterviewTurnType.CORE_QUESTION);
        coreTurn.setText(question.getContent());
        coreTurn.setAnswerStatus(InterviewTurnAnswerStatus.WAITING);
        session.setCurrentTurn(coreTurn);

        aggregate = new InterviewAnswer();
        aggregate.setId(UUID.randomUUID());
        aggregate.setSession(session);
        aggregate.setQuestionId(question.getId());
        aggregate.setEvidenceSummaryJson(Map.of());

        when(sessionRepository.findOwnedForUpdate(session.getId(), candidate.getId()))
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(any(InterviewSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(turnRepository.findByIdAndSessionId(coreTurn.getId(), session.getId()))
                .thenReturn(Optional.of(coreTurn));
        when(turnRepository.findBySessionIdAndAnswerClientId(any(), any()))
                .thenReturn(Optional.empty());
        when(turnRepository.findBySessionIdAndAssessmentItemIdOrderBySequenceNoAsc(
                session.getId(), question.getId())).thenReturn(List.of(coreTurn));
        when(turnRepository.save(any(InterviewConversationTurn.class)))
                .thenAnswer(invocation -> {
                    InterviewConversationTurn turn = invocation.getArgument(0);
                    if (turn.getId() == null) turn.setId(UUID.randomUUID());
                    return turn;
                });
        when(answerRepository.findBySessionIdAndQuestionId(session.getId(), question.getId()))
                .thenReturn(Optional.of(aggregate));
        when(answerRepository.save(any(InterviewAnswer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(answerRepository.saveAndFlush(any(InterviewAnswer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactions.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
    }

    @Test
    void probeKeepsAggregateOpenAndCreatesOneAnswerableFollowUp() {
        when(shopAiKeyClient.analyzeAssessmentTurn(any(), any(), any(), any(), any()))
                .thenReturn(analysis(ShopAiKeyClient.AnswerAnalysisAction.PROBE,
                        "Bạn đã trực tiếp làm gì để xử lý bất đồng?"));

        AiInterviewConversationService.ConversationResult result = service.confirmTurn(
                session.getId(), coreTurn.getId(), UUID.randomUUID(), 1,
                "Tôi trao đổi với cả nhóm.", "Tôi trao đổi với cả nhóm."
        );

        assertThat(result.action()).isEqualTo(AnswerAnalysisAction.PROBE);
        assertThat(result.itemCompleted()).isFalse();
        assertThat(session.getDialogueState()).isEqualTo(InterviewDialogueState.WAITING_ANSWER);
        assertThat(session.getCurrentTurn().getTurnType()).isEqualTo(InterviewTurnType.PROBE);
        assertThat(session.getAssessmentTurnCount()).isEqualTo(2);
        assertThat(aggregate.getAnsweredAt()).isNull();
        verify(answerRepository, never()).saveAndFlush(any());
    }

    @Test
    void nextGroupsConfirmedEvidenceAndAddsNeutralAcknowledgement() {
        when(shopAiKeyClient.analyzeAssessmentTurn(any(), any(), any(), any(), any()))
                .thenReturn(analysis(ShopAiKeyClient.AnswerAnalysisAction.NEXT, null));

        AiInterviewConversationService.ConversationResult result = service.confirmTurn(
                session.getId(), coreTurn.getId(), UUID.randomUUID(), 1,
                "Tôi lắng nghe rồi thống nhất phương án.",
                "Tôi lắng nghe rồi thống nhất phương án và theo dõi kết quả."
        );

        assertThat(result.itemCompleted()).isTrue();
        assertThat(aggregate.getAnsweredAt()).isNotNull();
        assertThat(aggregate.getFinalTranscript()).contains("theo dõi kết quả");
        assertThat(aggregate.isTranscriptEdited()).isTrue();
        assertThat(session.getDialogueState()).isEqualTo(InterviewDialogueState.ACK_TRANSITION);
        assertThat(session.getCurrentTurn().getTurnType())
                .isEqualTo(InterviewTurnType.ACKNOWLEDGEMENT);
        assertThat(session.getCurrentTurn().getAnswerStatus())
                .isEqualTo(InterviewTurnAnswerStatus.NOT_REQUIRED);
    }

    @Test
    void deterministicLimitOverridesProviderProbeAndStillCompletesItem() {
        session.setAssessmentTurnCount(6);
        when(shopAiKeyClient.analyzeAssessmentTurn(any(), any(), any(), any(), any()))
                .thenReturn(analysis(ShopAiKeyClient.AnswerAnalysisAction.PROBE,
                        "Bạn có thể nói thêm không?"));

        AiInterviewConversationService.ConversationResult result = service.confirmTurn(
                session.getId(), coreTurn.getId(), UUID.randomUUID(), 1,
                "Một câu trả lời.", "Một câu trả lời."
        );

        assertThat(result.action()).isEqualTo(AnswerAnalysisAction.NEXT);
        assertThat(result.itemCompleted()).isTrue();
        assertThat(session.getAssessmentTurnCount()).isEqualTo(6);
    }

    @Test
    void confirmingAiCorrectedDraftDoesNotCountAsCandidateEdit() {
        coreTurn.setCandidateRawAnswer("em dùng spring bút");
        coreTurn.setCandidateFinalAnswer("em dùng Spring Boot");
        coreTurn.setAnswerStatus(InterviewTurnAnswerStatus.REVIEWING);
        when(shopAiKeyClient.analyzeAssessmentTurn(any(), any(), any(), any(), any()))
                .thenReturn(analysis(ShopAiKeyClient.AnswerAnalysisAction.NEXT, null));

        service.confirmTurn(
                session.getId(), coreTurn.getId(), UUID.randomUUID(), 1,
                "em dùng spring bút", "em dùng Spring Boot"
        );

        assertThat(coreTurn.isTranscriptEdited()).isFalse();
        assertThat(coreTurn.getEditCount()).isZero();
        assertThat(aggregate.isTranscriptEdited()).isFalse();
        assertThat(aggregate.getTranscriptEditCount()).isZero();
    }

    @Test
    void candidateChangeAfterAiCorrectionCountsAsManualEdit() {
        coreTurn.setCandidateRawAnswer("em dùng spring bút");
        coreTurn.setCandidateFinalAnswer("em dùng Spring Boot");
        coreTurn.setAnswerStatus(InterviewTurnAnswerStatus.REVIEWING);
        when(shopAiKeyClient.analyzeAssessmentTurn(any(), any(), any(), any(), any()))
                .thenReturn(analysis(ShopAiKeyClient.AnswerAnalysisAction.NEXT, null));

        service.confirmTurn(
                session.getId(), coreTurn.getId(), UUID.randomUUID(), 1,
                "em dùng spring bút", "em dùng Spring Boot và Postman"
        );

        assertThat(coreTurn.isTranscriptEdited()).isTrue();
        assertThat(coreTurn.getEditCount()).isEqualTo(1);
        assertThat(aggregate.isTranscriptEdited()).isTrue();
        assertThat(aggregate.getTranscriptEditCount()).isEqualTo(1);
    }

    @Test
    void passesCorrectionEvidenceAsUntrustedDraftOnlyAfterConfirmation() {
        UUID captureId = UUID.randomUUID();
        aggregate.setActiveCaptureId(captureId);
        aggregate.setActiveCaptureVersion(1);
        coreTurn.setCandidateRawAnswer("em dùng spring bút");
        coreTurn.setCandidateFinalAnswer("em dùng Spring Boot");
        coreTurn.setAnswerStatus(InterviewTurnAnswerStatus.REVIEWING);
        InterviewAnswerCapture capture = new InterviewAnswerCapture();
        capture.setAnswer(aggregate);
        capture.setConversationTurn(coreTurn);
        capture.setCaptureId(captureId);
        capture.setCaptureVersion(1);
        capture.setTranscriptCorrectionStatus(TranscriptCorrectionStatus.CORRECTED);
        capture.setTranscriptCorrectionJson(Map.of(
                "evidence", List.of(Map.of("type", "technical_knowledge", "text", "Dùng Spring Boot")),
                "answerSummary", "Ứng viên dùng Spring Boot.",
                "followUpNeeded", true,
                "followUpReason", "Chưa nêu kết quả."));
        when(captureRepository.findByAnswerIdAndCaptureIdAndCaptureVersion(
                aggregate.getId(), captureId, 1)).thenReturn(Optional.of(capture));
        when(shopAiKeyClient.analyzeAssessmentTurn(
                any(), any(), any(), any(), any(), any()))
                .thenReturn(analysis(ShopAiKeyClient.AnswerAnalysisAction.NEXT, null));

        service.confirmTurn(
                session.getId(), coreTurn.getId(), UUID.randomUUID(), 1,
                "em dùng spring bút", "em dùng Spring Boot"
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> evidenceCaptor = ArgumentCaptor.forClass(Map.class);
        verify(shopAiKeyClient).analyzeAssessmentTurn(
                eq(session), eq(question), eq("em dùng Spring Boot"),
                any(), evidenceCaptor.capture(), any());
        assertThat(evidenceCaptor.getValue())
                .containsEntry("answerSummary", "Ứng viên dùng Spring Boot.")
                .containsEntry("followUpNeeded", true)
                .containsKey("evidence");
    }

    @Test
    void providerFailureUsesNextFallbackAndDoesNotBlockConversation() {
        when(shopAiKeyClient.analyzeAssessmentTurn(any(), any(), any(), any(), any()))
                .thenThrow(new AiProviderException("AI_PROVIDER_FAILED", "Provider unavailable"));

        AiInterviewConversationService.ConversationResult result = service.confirmTurn(
                session.getId(), coreTurn.getId(), UUID.randomUUID(), 1,
                "Câu trả lời gốc.", "Câu trả lời gốc."
        );

        assertThat(result.action()).isEqualTo(AnswerAnalysisAction.NEXT);
        assertThat(result.itemCompleted()).isTrue();
        assertThat(coreTurn.getAnswerStatus()).isEqualTo(InterviewTurnAnswerStatus.CONFIRMED);
        assertThat(coreTurn.getCandidateFinalAnswer()).isEqualTo("Câu trả lời gốc.");
        assertThat(aggregate.getAnsweredAt()).isNotNull();
        assertThat(session.getDialogueState()).isEqualTo(InterviewDialogueState.ACK_TRANSITION);
        assertThat(session.getCurrentTurn().getTurnType()).isEqualTo(InterviewTurnType.ACKNOWLEDGEMENT);
        assertThat(session.getLastErrorStage()).isNull();
        assertThat(session.getLastErrorCode()).isNull();
    }

    @Test
    void schemaFailurePreservesPreviousSummaryAndContinuesWithoutRetry() {
        aggregate.setEvidenceSummaryJson(Map.of("summary", "Đã ghi nhận evidence trước đó."));
        when(shopAiKeyClient.analyzeAssessmentTurn(any(), any(), any(), any(), any()))
                .thenThrow(new AiProviderException(
                        "AI_INVALID_ANALYSIS_SCHEMA",
                        "AI phải trả array hợp lệ cho trường weakEvidence"));

        AiInterviewConversationService.ConversationResult result = service.confirmTurn(
                session.getId(), coreTurn.getId(), UUID.randomUUID(), 1,
                "Tôi đã làm leader của nhóm.", "Tôi đã làm leader của nhóm."
        );

        assertThat(result.action()).isEqualTo(AnswerAnalysisAction.NEXT);
        assertThat(aggregate.getEvidenceSummaryJson())
                .containsEntry("summary", "Đã ghi nhận evidence trước đó.");
        verify(shopAiKeyClient).analyzeAssessmentTurn(any(), any(), any(), any(), any());
    }

    @Test
    void cannotConfirmWhileTranscriptCorrectionIsPending() {
        UUID captureId = UUID.randomUUID();
        aggregate.setActiveCaptureId(captureId);
        aggregate.setActiveCaptureVersion(1);
        InterviewAnswerCapture capture = new InterviewAnswerCapture();
        capture.setAnswer(aggregate);
        capture.setConversationTurn(coreTurn);
        capture.setCaptureId(captureId);
        capture.setCaptureVersion(1);
        capture.setTranscriptCorrectionStatus(TranscriptCorrectionStatus.PENDING);
        when(captureRepository.findByAnswerIdAndCaptureIdAndCaptureVersion(
                aggregate.getId(), captureId, 1)).thenReturn(Optional.of(capture));

        assertThatThrownBy(() -> service.confirmTurn(
                session.getId(), coreTurn.getId(), UUID.randomUUID(), 1,
                "em dùng spring bút", "em dùng spring bút"
        )).isInstanceOf(com.sjp.recruitment.exception.ApiException.class)
                .extracting(exception -> ((com.sjp.recruitment.exception.ApiException) exception).getCode())
                .isEqualTo("TRANSCRIPT_CORRECTION_PENDING");
        assertThat(coreTurn.getAnswerStatus()).isEqualTo(InterviewTurnAnswerStatus.WAITING);
        verify(shopAiKeyClient, never()).analyzeAssessmentTurn(any(), any(), any(), any(), any());
    }

    @Test
    void duplicateClientIdReturnsExistingSnapshotWithoutCallingProviderAgain() {
        UUID clientId = UUID.randomUUID();
        coreTurn.setAnswerClientId(clientId);
        coreTurn.setAnswerStatus(InterviewTurnAnswerStatus.CONFIRMED);
        coreTurn.setCandidateRawAnswer("Câu trả lời.");
        coreTurn.setCandidateFinalAnswer("Câu trả lời.");
        when(turnRepository.findBySessionIdAndAnswerClientId(session.getId(), clientId))
                .thenReturn(Optional.of(coreTurn));

        AiInterviewConversationService.ConversationResult result = service.confirmTurn(
                session.getId(), coreTurn.getId(), clientId, 1,
                "Câu trả lời.", "Câu trả lời."
        );

        assertThat(result.idempotent()).isTrue();
        verify(shopAiKeyClient, never()).analyzeAssessmentTurn(any(), any(), any(), any(), any());
    }

    @Test
    void initializesOpeningAndOnlyPublishesTheFirstCoreQuestion() {
        session.setDialogueState(null);
        session.setCurrentTurn(null);
        session.setNextTurnSequence(1);
        session.setAssessmentTurnCount(0);
        session.setEvaluationProfile(Map.of("targetRole", "Java Backend Developer"));
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(question));

        service.initialize(session);

        assertThat(session.getDialogueState()).isEqualTo(InterviewDialogueState.WAITING_ANSWER);
        assertThat(session.getCurrentTurn().getTurnType()).isEqualTo(InterviewTurnType.CORE_QUESTION);
        assertThat(session.getCurrentTurn().getText()).isEqualTo(question.getContent());
        assertThat(session.getCurrentTurn().getSequenceNo()).isEqualTo(2);
        assertThat(session.getNextTurnSequence()).isEqualTo(3);
        assertThat(session.getAssessmentTurnCount()).isEqualTo(1);
    }

    @Test
    void advancesFromAcknowledgementToTheNextExistingCoreQuestion() {
        InterviewConversationTurn acknowledgement = new InterviewConversationTurn();
        acknowledgement.setId(UUID.randomUUID());
        acknowledgement.setSession(session);
        acknowledgement.setSequenceNo(3);
        acknowledgement.setTurnType(InterviewTurnType.ACKNOWLEDGEMENT);
        acknowledgement.setText("Được rồi.");
        acknowledgement.setAnswerStatus(InterviewTurnAnswerStatus.NOT_REQUIRED);
        session.setDialogueState(InterviewDialogueState.ACK_TRANSITION);
        session.setCurrentTurn(acknowledgement);
        session.setNextTurnSequence(4);
        aggregate.setAnsweredAt(java.time.LocalDateTime.now());
        InterviewQuestion second = new InterviewQuestion();
        second.setId(UUID.randomUUID());
        second.setSession(session);
        second.setOrderIndex(2);
        second.setContent("Bạn xử lý một lỗi backend khó như thế nào?");
        second.setQuestionType("technical");
        second.setCompetencyId("problem-solving");
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(question, second));
        when(answerRepository.findBySessionIdOrderByAnsweredAtAsc(session.getId()))
                .thenReturn(List.of(aggregate));

        AiInterviewConversationService.AdvanceResult result =
                service.advanceAfterCompletedItem(session.getId());

        assertThat(result).isEqualTo(AiInterviewConversationService.AdvanceResult.NEXT_CORE_READY);
        assertThat(session.getDialogueState()).isEqualTo(InterviewDialogueState.WAITING_ANSWER);
        assertThat(session.getCurrentTurn().getAssessmentItem()).isSameAs(second);
        assertThat(session.getCurrentTurn().getTurnType()).isEqualTo(InterviewTurnType.CORE_QUESTION);
        assertThat(session.getAssessmentTurnCount()).isEqualTo(2);
    }

    private ShopAiKeyClient.AnswerAnalysisDraft analysis(
            ShopAiKeyClient.AnswerAnalysisAction action,
            String followUp
    ) {
        return new ShopAiKeyClient.AnswerAnalysisDraft(
                action,
                List.of("đã nêu hành động"),
                Map.of("situation", true, "task", true, "action", true, "result", true),
                List.of(),
                followUp,
                "Ứng viên đã nêu hành động và kết quả.",
                new ShopAiKeyClient.GlobalEvidenceDeltaDraft(
                        List.of("teamwork"), List.of(), List.of(), List.of())
        );
    }
}
