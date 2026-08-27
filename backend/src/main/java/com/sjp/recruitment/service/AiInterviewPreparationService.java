package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewBackgroundConfiguration;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.AiInterviewPracticeSessionRequest;
import com.sjp.recruitment.model.dto.response.AiInterviewPreparationResponse;
import com.sjp.recruitment.model.dto.response.AiInterviewSessionResponse;
import com.sjp.recruitment.model.entity.AiInterviewPreparationJob;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.repository.AiInterviewPreparationJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class AiInterviewPreparationService {

    private final CandidateService candidateService;
    private final AiInterviewService aiInterviewService;
    private final AiInterviewPreparationJobRepository jobRepository;
    private final AiInterviewPreparationProgressWriter progressWriter;
    private final TaskExecutor executor;

    public AiInterviewPreparationService(
            CandidateService candidateService,
            AiInterviewService aiInterviewService,
            AiInterviewPreparationJobRepository jobRepository,
            AiInterviewPreparationProgressWriter progressWriter,
            @Qualifier(AiInterviewBackgroundConfiguration.EXECUTOR_BEAN) TaskExecutor executor
    ) {
        this.candidateService = candidateService;
        this.aiInterviewService = aiInterviewService;
        this.jobRepository = jobRepository;
        this.progressWriter = progressWriter;
        this.executor = executor;
    }

    @Transactional
    public AiInterviewPreparationResponse start(AiInterviewPracticeSessionRequest request) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        AiInterviewPreparationJob job = new AiInterviewPreparationJob();
        job.setCandidate(candidate);
        job.setStatus("QUEUED");
        job.setStage("VALIDATING");
        job.setProgress(5);
        job.setMessage("Đang kiểm tra yêu cầu");
        job = jobRepository.save(job);

        UUID jobId = job.getId();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Runnable submit = () -> submit(jobId, request, authentication);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit.run();
                }
            });
        } else {
            submit.run();
        }
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public AiInterviewPreparationResponse get(String preparationId) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        UUID id;
        try {
            id = UUID.fromString(preparationId);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "PREPARATION_ID_INVALID", "Mã tiến trình không hợp lệ.");
        }
        AiInterviewPreparationJob job = jobRepository.findByIdAndCandidateId(id, candidate.getId())
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "PREPARATION_NOT_FOUND", "Không tìm thấy tiến trình chuẩn bị phỏng vấn."));
        return toResponse(job);
    }

    private void submit(
            UUID jobId,
            AiInterviewPracticeSessionRequest request,
            Authentication authentication
    ) {
        try {
            executor.execute(() -> run(jobId, request, authentication));
        } catch (RuntimeException exception) {
            log.warn("AI interview preparation job rejected: preparationId={}, code={}",
                    jobId, exception.getClass().getSimpleName());
            progressWriter.failed(jobId, "PREPARATION_EXECUTOR_REJECTED",
                    "Hệ thống đang xử lý nhiều phiên. Vui lòng thử lại.");
        }
    }

    private void run(
            UUID jobId,
            AiInterviewPracticeSessionRequest request,
            Authentication authentication
    ) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        AtomicReference<String> warning = new AtomicReference<>();
        try {
            progressWriter.update(jobId, "VALIDATING", 5,
                    "Đang kiểm tra yêu cầu", null);
            AiInterviewSessionResponse session = aiInterviewService.createPracticeSession(
                    request,
                    (stage, progress, message, warningMessage) -> {
                        if (warningMessage != null && !warningMessage.isBlank()) {
                            warning.set(warningMessage);
                        }
                        progressWriter.update(jobId, stage, progress, message, warningMessage);
                    }
            );
            progressWriter.ready(jobId, UUID.fromString(session.id()), warning.get());
        } catch (ApiException exception) {
            progressWriter.failed(jobId, exception.getCode(), safeMessage(exception));
        } catch (RuntimeException exception) {
            log.error("AI interview preparation failed: preparationId={}, exceptionType={}",
                    jobId, exception.getClass().getSimpleName(), exception);
            progressWriter.failed(jobId, "PRACTICE_PREPARATION_FAILED",
                    "Không thể chuẩn bị phiên phỏng vấn. Vui lòng thử lại.");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private AiInterviewPreparationResponse toResponse(AiInterviewPreparationJob job) {
        return new AiInterviewPreparationResponse(
                job.getId().toString(),
                job.getStatus(),
                job.getStage(),
                job.getProgress() == null ? 0 : job.getProgress(),
                job.getMessage(),
                job.getWarningMessage(),
                job.getSession() == null ? null : job.getSession().getId().toString(),
                job.getErrorCode(),
                job.getErrorMessage(),
                job.getCreatedAt() == null ? null : job.getCreatedAt().toString(),
                job.getUpdatedAt() == null ? null : job.getUpdatedAt().toString()
        );
    }

    private String safeMessage(ApiException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Không thể chuẩn bị phiên phỏng vấn."
                : exception.getMessage();
    }
}
