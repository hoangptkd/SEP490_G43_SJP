package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.AiInterviewPreparationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiInterviewPreparationJobRepository
        extends JpaRepository<AiInterviewPreparationJob, UUID> {

    Optional<AiInterviewPreparationJob> findByIdAndCandidateId(UUID id, UUID candidateId);
}
