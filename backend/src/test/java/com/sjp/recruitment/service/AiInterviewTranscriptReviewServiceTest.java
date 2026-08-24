package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewAnswerCapture;
import com.sjp.recruitment.model.entity.InterviewConversationTurn;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.enums.InterviewDialogueState;
import com.sjp.recruitment.model.enums.InterviewTurnAnswerStatus;
import com.sjp.recruitment.model.enums.InterviewTurnType;
import com.sjp.recruitment.model.enums.TranscriptCorrectionStatus;
import com.sjp.recruitment.model.enums.TranscriptReviewAction;
import com.sjp.recruitment.repository.InterviewAnswerCaptureRepository;
import com.sjp.recruitment.repository.InterviewAnswerRepository;
import com.sjp.recruitment.repository.InterviewConversationTurnRepository;
import com.sjp.recruitment.repository.InterviewSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInterviewTranscriptReviewServiceTest {

    @Mock private CandidateService candidateService;
    @Mock private InterviewSessionRepository sessionRepository;
    @Mock private InterviewConversationTurnRepository turnRepository;
    @Mock private InterviewAnswerRepository answerRepository;
    @Mock private InterviewAnswerCaptureRepository captureRepository;
    @Mock private TransactionTemplate transactions;

    private AiInterviewTranscriptReviewService service;
    private CandidateProfile candidate;
    private InterviewSession session;
    private InterviewQuestion question;
    private InterviewConversationTurn turn;
    private InterviewAnswer answer;
    private InterviewAnswerCapture capture;

    @BeforeEach
    void setUp() {
        AiInterviewProperties properties = new AiInterviewProperties();
        service = new AiInterviewTranscriptReviewService(
                candidateService,
                sessionRepository,
                turnRepository,
                answerRepository,
                captureRepository,
                new AiInterviewConversationPolicy(properties),
                transactions);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });

        candidate = new CandidateProfile();
        candidate.setId(UUID.randomUUID());
        session = new InterviewSession();
        session.setId(UUID.randomUUID());
        session.setDialogueState(InterviewDialogueState.WAITING_ANSWER);
        session.setDialogueVersion(7);
        question = new InterviewQuestion();
        question.setId(UUID.randomUUID());
        question.setSession(session);
        turn = new InterviewConversationTurn();
        turn.setId(UUID.randomUUID());
        turn.setSession(session);
        turn.setAssessmentItem(question);
        turn.setTurnType(InterviewTurnType.CORE_QUESTION);
        turn.setAnswerStatus(InterviewTurnAnswerStatus.CONFIRMED);
        turn.setCandidateRawAnswer("Em dùng spring bút.");
        turn.setCandidateFinalAnswer("Em dùng spring bút.");
        answer = new InterviewAnswer();
        answer.setId(UUID.randomUUID());
        answer.setSession(session);
        answer.setQuestionId(question.getId());
        answer.setRawTranscript(turn.getCandidateRawAnswer());
        answer.setFinalTranscript(turn.getCandidateFinalAnswer());
        answer.setTranscriptText(turn.getCandidateFinalAnswer());
        capture = new InterviewAnswerCapture();
        capture.setId(UUID.randomUUID());
        capture.setAnswer(answer);
        capture.setConversationTurn(turn);
        capture.setCaptureId(UUID.randomUUID());
        capture.setCaptureVersion(1);
        capture.setRawTranscript(turn.getCandidateRawAnswer());
        capture.setCorrectedTranscript("Em dùng Spring Boot.");
        capture.setTranscriptCorrectionStatus(TranscriptCorrectionStatus.CORRECTED);
        capture.setTranscriptCorrectionJson(new LinkedHashMap<>(Map.of(
                "candidateDecision", "PENDING",
                "corrections", List.of())));

        when(candidateService.getCurrentCandidateProfile()).thenReturn(candidate);
        when(sessionRepository.findOwnedForUpdate(session.getId(), candidate.getId()))
                .thenReturn(Optional.of(session));
        lenient().when(turnRepository.findByIdAndSessionIdForUpdate(turn.getId(), session.getId()))
                .thenReturn(Optional.of(turn));
        lenient().when(answerRepository.findBySessionIdAndQuestionIdForUpdate(session.getId(), question.getId()))
                .thenReturn(Optional.of(answer));
        lenient().when(captureRepository.findForUpdate(answer.getId(), capture.getCaptureId(), 1))
                .thenReturn(Optional.of(capture));
        lenient().when(turnRepository.findBySessionIdAndAssessmentItemIdOrderBySequenceNoAsc(
                session.getId(), question.getId())).thenReturn(List.of(turn));
    }

    @Test
    void acceptingAiCorrectionUpdatesTimelineAndAggregateWithoutRerunningEvidence() {
        service.review(
                session.getId(),
                turn.getId(),
                TranscriptReviewAction.ACCEPT_AI,
                capture.getCaptureId(),
                1,
                null,
                0);

        assertThat(turn.getCandidateFinalAnswer()).isEqualTo("Em dùng Spring Boot.");
        assertThat(turn.isTranscriptEdited()).isTrue();
        assertThat(turn.getEditCount()).isEqualTo(1);
        assertThat(answer.getFinalTranscript()).isEqualTo("Em dùng Spring Boot.");
        assertThat(answer.getTranscriptEditCount()).isEqualTo(1);
        assertThat(capture.getTranscriptCorrectionJson())
                .containsEntry("candidateDecision", "ACCEPTED")
                .containsEntry("decidedTranscript", "Em dùng Spring Boot.");
    }

    @Test
    void completingReviewAutoKeepsUnresolvedCorrectionsAndMovesToClosing() {
        session.setDialogueState(InterviewDialogueState.REVIEW_TRANSCRIPTS);
        when(answerRepository.findBySessionIdOrderByAnsweredAtAsc(session.getId()))
                .thenReturn(List.of(answer));
        when(captureRepository.findByAnswerIdInForUpdate(List.of(answer.getId())))
                .thenReturn(List.of(capture));

        service.completeReview(session.getId());

        assertThat(session.getDialogueState()).isEqualTo(InterviewDialogueState.CLOSING);
        assertThat(session.getDialogueVersion()).isEqualTo(8);
        assertThat(capture.getTranscriptCorrectionJson())
                .containsEntry("candidateDecision", "AUTO_KEPT")
                .containsEntry("decidedTranscript", "Em dùng spring bút.");
    }
}
