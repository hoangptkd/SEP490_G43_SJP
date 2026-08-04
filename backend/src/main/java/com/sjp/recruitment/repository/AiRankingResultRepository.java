package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.AiRankingResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiRankingResultRepository extends JpaRepository<AiRankingResult, UUID> {
    Optional<AiRankingResult> findByApplicationId(UUID applicationId);
}
