package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.SavedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedJobRepository extends JpaRepository<SavedJob, UUID> {
    List<SavedJob> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);
    Optional<SavedJob> findByCandidateIdAndJobId(UUID candidateId, UUID jobId);
    boolean existsByCandidateIdAndJobId(UUID candidateId, UUID jobId);
    long countByCandidateId(UUID candidateId);
}
