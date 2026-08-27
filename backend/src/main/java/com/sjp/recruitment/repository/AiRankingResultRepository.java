package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.AiRankingResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface AiRankingResultRepository extends JpaRepository<AiRankingResult, UUID> {
    Optional<AiRankingResult> findByApplicationId(UUID applicationId);

    @Query("SELECT COUNT(r) FROM AiRankingResult r WHERE r.application.job.employer.user.id = :userId")
    long countByEmployerUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(r) FROM AiRankingResult r WHERE r.application.job.employer.user.id = :userId AND r.rankedAt >= :startOfMonth")
    long countByEmployerUserIdSince(@Param("userId") UUID userId, @Param("startOfMonth") java.time.LocalDateTime startOfMonth);
}
