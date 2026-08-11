package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID userId);
    Page<Notification> findByRecipientUserId(UUID userId, Pageable pageable);
    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID userId);
    long countByRecipientUserIdAndReadFalse(UUID userId);
}
