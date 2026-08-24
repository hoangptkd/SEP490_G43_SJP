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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiInterviewTranscriptCorrectionCoordinator {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewConversationTurnRepository turnRepository;
    private final InterviewAnswerRepository answerRepository;
    private final InterviewAnswerCaptureRepository captureRepository;
    private final TranscriptCorrectionContextBuilder contextBuilder;
    private final TranscriptCorrectionService correctionService;

    @Transactional(readOnly = true)
    public void submitAfterDecision(UUID sessionId, UUID turnId) {
        if (sessionId == null || turnId == null) return;
        try {
            InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
            InterviewConversationTurn turn = turnRepository
                    .findByIdAndSessionId(turnId, sessionId)
                    .orElse(null);
            InterviewQuestion question = turn == null ? null : turn.getAssessmentItem();
            if (session == null || question == null || question.getId() == null) return;

            InterviewAnswer answer = answerRepository
                    .findBySessionIdAndQuestionId(sessionId, question.getId())
                    .orElse(null);
            if (answer == null || answer.getActiveCaptureId() == null
                    || answer.getActiveCaptureVersion() == null) return;

            UUID captureId = answer.getActiveCaptureId();
            int captureVersion = answer.getActiveCaptureVersion();
            InterviewAnswerCapture capture = captureRepository
                    .findByAnswerIdAndCaptureIdAndCaptureVersion(
                            answer.getId(), captureId, captureVersion)
                    .orElse(null);
            if (capture == null
                    || capture.getTranscriptCorrectionStatus() != TranscriptCorrectionStatus.PENDING) return;

            TranscriptCorrectionContext context = contextBuilder.build(
                    session,
                    question,
                    capture.getRawTranscript(),
                    answer.getEvidenceSummaryJson());
            correctionService.submit(
                    sessionId, answer.getId(), captureId, captureVersion, context);
        } catch (RuntimeException exception) {
            log.warn("Transcript correction could not be queued after decision: sessionId={}, "
                            + "turnId={}, code={}",
                    sessionId, turnId, exception.getClass().getSimpleName());
        }
    }
}
