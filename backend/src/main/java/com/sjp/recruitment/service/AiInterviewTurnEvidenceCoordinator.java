package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewBackgroundConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class AiInterviewTurnEvidenceCoordinator {

    private final TaskExecutor executor;
    private final AiInterviewTurnEvidenceService evidenceService;
    private final Map<UUID, CompletableFuture<Void>> sessionTails = new HashMap<>();
    private final Map<AiInterviewTurnEvidenceService.EvidenceJob, CompletableFuture<Void>> jobs =
            new HashMap<>();

    public AiInterviewTurnEvidenceCoordinator(
            @Qualifier(AiInterviewBackgroundConfiguration.EXECUTOR_BEAN) TaskExecutor executor,
            AiInterviewTurnEvidenceService evidenceService
    ) {
        this.executor = executor;
        this.evidenceService = evidenceService;
    }

    public synchronized CompletableFuture<Void> submit(
            UUID sessionId,
            UUID turnId,
            UUID answerClientId
    ) {
        AiInterviewTurnEvidenceService.EvidenceJob key =
                new AiInterviewTurnEvidenceService.EvidenceJob(
                        sessionId, turnId, answerClientId);
        CompletableFuture<Void> existing = jobs.get(key);
        if (existing != null) return existing;

        CompletableFuture<Void> predecessor = sessionTails.getOrDefault(
                sessionId, CompletableFuture.completedFuture(null));
        CompletableFuture<Void> job;
        try {
            job = predecessor
                    .handle((ignored, failure) -> null)
                    .thenRunAsync(() -> evidenceService.enrich(key), executor);
        } catch (RuntimeException exception) {
            evidenceService.markFailed(
                    key, "EVIDENCE_EXECUTOR_REJECTED", exception.getMessage());
            log.warn("AI turn evidence job rejected: sessionId={}, turnId={}, code={}",
                    sessionId, turnId, exception.getClass().getSimpleName());
            return CompletableFuture.failedFuture(exception);
        }
        jobs.put(key, job);
        sessionTails.put(sessionId, job);
        job.whenComplete((ignored, failure) -> removeCompleted(key, job));
        return job;
    }

    private synchronized void removeCompleted(
            AiInterviewTurnEvidenceService.EvidenceJob key,
            CompletableFuture<Void> completed
    ) {
        jobs.remove(key, completed);
        sessionTails.remove(key.sessionId(), completed);
    }
}
