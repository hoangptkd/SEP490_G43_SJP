package com.sjp.recruitment.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AiInterviewAdaptivePrefetchCoordinatorTest {

    @Test
    void waitsForEvidenceWithoutBlockingCallerThenStartsGeneration() {
        Queue<Runnable> queued = new ArrayDeque<>();
        TaskExecutor executor = queued::add;
        AiInterviewAdaptivePrefetchCoordinator coordinator =
                new AiInterviewAdaptivePrefetchCoordinator(executor);
        CompletableFuture<Void> evidence = new CompletableFuture<>();
        AtomicInteger generations = new AtomicInteger();

        CompletableFuture<Void> adaptive = coordinator.startAfter(
                UUID.randomUUID(), evidence, generations::incrementAndGet);

        assertThat(adaptive).isNotCompleted();
        assertThat(queued).isEmpty();
        evidence.complete(null);
        assertThat(queued).hasSize(1);
        queued.remove().run();
        assertThat(adaptive).isCompleted();
        assertThat(generations).hasValue(1);
    }

    @Test
    void stillStartsGenerationWhenEvidenceFails() {
        Queue<Runnable> queued = new ArrayDeque<>();
        TaskExecutor executor = queued::add;
        AiInterviewAdaptivePrefetchCoordinator coordinator =
                new AiInterviewAdaptivePrefetchCoordinator(executor);
        CompletableFuture<Void> evidence = new CompletableFuture<>();
        AtomicInteger generations = new AtomicInteger();

        CompletableFuture<Void> adaptive = coordinator.startAfter(
                UUID.randomUUID(), evidence, generations::incrementAndGet);
        evidence.completeExceptionally(new IllegalStateException("provider failed"));

        assertThat(queued).hasSize(1);
        queued.remove().run();
        assertThat(adaptive).isCompleted();
        assertThat(generations).hasValue(1);
    }
}
