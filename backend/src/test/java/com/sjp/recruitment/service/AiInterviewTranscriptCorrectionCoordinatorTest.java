package com.sjp.recruitment.service;

import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewAnswerCapture;
import com.sjp.recruitment.model.entity.InterviewConversationTurn;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.enums.TranscriptCorrectionStatus;
import com.sjp.recruitment.repository.InterviewAnswerCaptureRepository;
import com.sjp.recruitment.repository.InterviewAnswerRepository;
import com.sjp.recruitment.repository.InterviewConversationTurnRepository;
import com.sjp.recruitment.repository.InterviewSessionRepository;
import com.sjp.recruitment.service.ai.TranscriptCorrectionContext;
import com.sjp.recruitment.service.ai.TranscriptCorrectionContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiInterviewTranscriptCorrectionCoordinatorTest {

    private final InterviewSessionRepository sessionRepository =
            mock(InterviewSessionRepository.class);
    private final InterviewConversationTurnRepository turnRepository =
            mock(InterviewConversationTurnRepository.class);
    private final InterviewAnswerRepository answerRepository =
            mock(InterviewAnswerRepository.class);
    private final InterviewAnswerCaptureRepository captureRepository =
            mock(InterviewAnswerCaptureRepository.class);
    private final TranscriptCorrectionContextBuilder contextBuilder =
            mock(TranscriptCorrectionContextBuilder.class);
    private final TranscriptCorrectionService correctionService =
            mock(TranscriptCorrectionService.class);
    private final AiInterviewTranscriptCorrectionCoordinator coordinator =
            new AiInterviewTranscriptCorrectionCoordinator(
                    sessionRepository,
                    turnRepository,
                    answerRepository,
                    captureRepository,
                    contextBuilder,
                    correctionService);

    private UUID sessionId;
    private UUID turnId;
    private UUID answerId;
    private UUID captureId;
    private InterviewAnswerCapture capture;
    private TranscriptCorrectionContext context;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        turnId = UUID.randomUUID();
        answerId = UUID.randomUUID();
        captureId = UUID.randomUUID();

        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        InterviewQuestion question = new InterviewQuestion();
        question.setId(UUID.randomUUID());
        question.setContent("Bạn đã bảo vệ refresh token như thế nào?");
        InterviewConversationTurn turn = new InterviewConversationTurn();
        turn.setId(turnId);
        turn.setSession(session);
        turn.setAssessmentItem(question);
        InterviewAnswer answer = new InterviewAnswer();
        answer.setId(answerId);
        answer.setSession(session);
        answer.setQuestionId(question.getId());
        answer.setActiveCaptureId(captureId);
        answer.setActiveCaptureVersion(1);
        answer.setEvidenceSummaryJson(Map.of());
        capture = new InterviewAnswerCapture();
        capture.setAnswer(answer);
        capture.setCaptureId(captureId);
        capture.setCaptureVersion(1);
        capture.setRawTranscript("Tôi lưu refresh token trong Redis với TTL.");
        capture.setTranscriptCorrectionStatus(TranscriptCorrectionStatus.PENDING);
        context = new TranscriptCorrectionContext(
                question.getContent(),
                capture.getRawTranscript(),
                List.of(), List.of(), List.of("Redis", "TTL"), "");

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(turnRepository.findByIdAndSessionId(turnId, sessionId)).thenReturn(Optional.of(turn));
        when(answerRepository.findBySessionIdAndQuestionId(sessionId, question.getId()))
                .thenReturn(Optional.of(answer));
        when(captureRepository.findByAnswerIdAndCaptureIdAndCaptureVersion(
                answerId, captureId, 1)).thenReturn(Optional.of(capture));
        when(contextBuilder.build(session, question, capture.getRawTranscript(), Map.of()))
                .thenReturn(context);
    }

    @Test
    void queuesPendingCorrectionOnlyAfterDecisionCompletes() {
        coordinator.submitAfterDecision(sessionId, turnId);

        verify(correctionService).submit(sessionId, answerId, captureId, 1, context);
    }

    @Test
    void doesNotQueueCorrectionThatAlreadyCompleted() {
        capture.setTranscriptCorrectionStatus(TranscriptCorrectionStatus.CORRECTED);

        coordinator.submitAfterDecision(sessionId, turnId);

        verify(correctionService, never()).submit(
                sessionId, answerId, captureId, 1, context);
    }
}
