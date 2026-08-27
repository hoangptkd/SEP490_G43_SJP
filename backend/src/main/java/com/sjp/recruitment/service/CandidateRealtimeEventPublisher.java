package com.sjp.recruitment.service;

import com.sjp.recruitment.model.dto.response.CandidateRealtimeEvent;
import com.sjp.recruitment.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateRealtimeEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public void publishAfterCommit(User user, String type, UUID entityId) {
        if (user == null || user.getEmail() == null || user.getRoleEnum() != User.UserRole.CANDIDATE) {
            return;
        }
        LocalDateTime occurredAt = LocalDateTime.now();
        CandidateRealtimeEvent event = new CandidateRealtimeEvent(
                UUID.randomUUID(),
                type,
                entityId,
                occurredAt,
                occurredAt.toInstant(ZoneOffset.UTC).toEpochMilli()
        );
        Runnable send = () -> messagingTemplate.convertAndSendToUser(
                user.getEmail(), "/queue/candidate-events", event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
