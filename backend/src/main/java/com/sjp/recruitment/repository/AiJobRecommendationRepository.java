package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.AiJobRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiJobRecommendationRepository extends JpaRepository<AiJobRecommendation, UUID> {
    List<AiJobRecommendation> findByCandidateIdAndRunIdOrderByRankPositionAsc(UUID candidateId, UUID runId);
    long deleteByCandidateId(UUID candidateId);
}
