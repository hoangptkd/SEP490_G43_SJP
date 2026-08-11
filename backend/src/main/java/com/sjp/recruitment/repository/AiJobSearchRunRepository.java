package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.AiJobSearchRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AiJobSearchRunRepository extends JpaRepository<AiJobSearchRun, UUID> {
    Optional<AiJobSearchRun> findFirstByCandidateIdAndStatusOrderByCreatedAtDesc(UUID candidateId, String status);
    long countByCandidateUserIdAndQuotaConsumedTrueAndCreatedAtGreaterThanEqual(UUID userId, LocalDateTime start);
    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
