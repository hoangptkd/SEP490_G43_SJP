package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.AiSessionFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface AiSessionFeedbackRepository extends JpaRepository<AiSessionFeedback, UUID> {
    Optional<AiSessionFeedback> findBySessionId(UUID sessionId);
    List<AiSessionFeedback> findBySessionIdIn(Collection<UUID> sessionIds);
}
