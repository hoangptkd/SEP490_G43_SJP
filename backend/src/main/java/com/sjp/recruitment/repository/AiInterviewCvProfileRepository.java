package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.AiInterviewCvProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiInterviewCvProfileRepository extends JpaRepository<AiInterviewCvProfile, UUID> {
    Optional<AiInterviewCvProfile> findFirstByCandidateIdAndCvIdAndContentHashAndPromptVersionAndModelUsedOrderByCreatedAtDesc(
            UUID candidateId,
            UUID cvId,
            String contentHash,
            String promptVersion,
            String modelUsed
    );
}
