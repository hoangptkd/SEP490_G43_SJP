package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.SavedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedJobRepository extends JpaRepository<SavedJob, UUID> {
    List<SavedJob> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);
    Page<SavedJob> findByCandidateId(UUID candidateId, Pageable pageable);
    Optional<SavedJob> findByCandidateIdAndJobId(UUID candidateId, UUID jobId);
    boolean existsByCandidateIdAndJobId(UUID candidateId, UUID jobId);
    long countByCandidateId(UUID candidateId);

    @Query("SELECT s.job.id FROM SavedJob s WHERE s.candidate.id = :candidateId AND s.job.id IN :jobIds")
    List<UUID> findSavedJobIds(@Param("candidateId") UUID candidateId, @Param("jobIds") List<UUID> jobIds);
}
