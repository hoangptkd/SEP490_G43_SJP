package com.sjp.recruitment.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AiInterviewTurnEvidenceCoordinatorTest {

    @Test
    void chainsSameSessionJobsAndDeduplicatesPendingIdentity() {
        Queue<Runnable> queued = new ArrayDeque<>();
        TaskExecutor executor = queued::add;
        AiInterviewTurnEvidenceService evidenceService = mock(AiInterviewTurnEvidenceService.class);
        AiInterviewTurnEvidenceCoordinator coordinator =
                new AiInterviewTurnEvidenceCoordinator(executor, evidenceService);
        UUID sessionId = UUID.randomUUID();
        UUID firstTurn = UUID.randomUUID();
        UUID firstClient = UUID.randomUUID();

        CompletableFuture<Void> first = coordinator.submit(sessionId, firstTurn, firstClient);
        CompletableFuture<Void> duplicate = coordinator.submit(sessionId, firstTurn, firstClient);
        CompletableFuture<Void> second = coordinator.submit(
                sessionId, UUID.randomUUID(), UUID.randomUUID());

        assertThat(duplicate).isSameAs(first);
        assertThat(queued).hasSize(1);
        queued.remove().run();
        assertThat(first).isCompleted();
        assertThat(second).isNotCompleted();
        assertThat(queued).hasSize(1);
        queued.remove().run();
        assertThat(second).isCompleted();
        verify(evidenceService, times(2))
                .enrich(any(AiInterviewTurnEvidenceService.EvidenceJob.class));
    }
}
