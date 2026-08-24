package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewBackgroundConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AiInterviewAdaptivePrefetchCoordinator {

    private final TaskExecutor executor;
    private final ConcurrentMap<UUID, CompletableFuture<Void>> jobs = new ConcurrentHashMap<>();

    public AiInterviewAdaptivePrefetchCoordinator(
            @Qualifier(AiInterviewBackgroundConfiguration.EXECUTOR_BEAN) TaskExecutor executor) {
        this.executor = executor;
    }

    public CompletableFuture<Void> start(UUID sessionId, Runnable generation) {
        return startAfter(sessionId, CompletableFuture.completedFuture(null), generation);
    }

    public CompletableFuture<Void> startAfter(
            UUID sessionId,
            CompletionStage<?> dependency,
            Runnable generation
    ) {
        CompletionStage<?> resolvedDependency = dependency == null
                ? CompletableFuture.completedFuture(null)
                : dependency;
        return jobs.computeIfAbsent(sessionId, ignored -> resolvedDependency
                .handle((value, failure) -> null)
                .thenRunAsync(generation, executor)
                .toCompletableFuture());
    }

    public void await(UUID sessionId, long timeoutMillis) {
        CompletableFuture<Void> job = jobs.get(sessionId);
        if (job == null) return;
        try {
            job.get(Math.max(1, timeoutMillis), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            log.warn("Adaptive question prefetch did not complete: sessionId={}, code={}",
                    sessionId, exception.getClass().getSimpleName());
        }
    }

    public void forget(UUID sessionId) {
        jobs.remove(sessionId);
    }
}
