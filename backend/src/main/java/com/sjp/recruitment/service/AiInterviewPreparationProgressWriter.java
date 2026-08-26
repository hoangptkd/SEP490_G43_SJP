package com.sjp.recruitment.service;

import com.sjp.recruitment.model.entity.AiInterviewPreparationJob;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.repository.AiInterviewPreparationJobRepository;
import com.sjp.recruitment.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiInterviewPreparationProgressWriter {

    private final AiInterviewPreparationJobRepository jobRepository;
    private final InterviewSessionRepository sessionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void update(
            UUID jobId,
            String stage,
            int progress,
            String message,
            String warningMessage
    ) {
        AiInterviewPreparationJob job = requireJob(jobId);
        job.setStatus("RUNNING");
        job.setStage(stage);
        job.setProgress(Math.max(0, Math.min(99, progress)));
        job.setMessage(message);
        if (warningMessage != null && !warningMessage.isBlank()) {
            job.setWarningMessage(warningMessage);
        }
        jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ready(UUID jobId, UUID sessionId, String warningMessage) {
        AiInterviewPreparationJob job = requireJob(jobId);
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("Prepared interview session is missing"));
        job.setSession(session);
        job.setStatus("READY");
        job.setStage("READY");
        job.setProgress(100);
        job.setMessage("Phòng phỏng vấn đã sẵn sàng");
        if (warningMessage != null && !warningMessage.isBlank()) {
            job.setWarningMessage(warningMessage);
        }
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(UUID jobId, String errorCode, String errorMessage) {
        AiInterviewPreparationJob job = requireJob(jobId);
        job.setStatus("FAILED");
        job.setStage("FAILED");
        job.setMessage("Không thể chuẩn bị phiên phỏng vấn");
        job.setErrorCode(errorCode);
        job.setErrorMessage(errorMessage);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    private AiInterviewPreparationJob requireJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Interview preparation job is missing"));
    }
}
