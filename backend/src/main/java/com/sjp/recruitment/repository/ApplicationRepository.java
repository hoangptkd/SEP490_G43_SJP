package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    Page<Application> findByCandidateId(UUID candidateId, Pageable pageable);
    Page<Application> findByCandidateUserId(UUID userId, Pageable pageable);
    Page<Application> findByJobId(UUID jobId, Pageable pageable);

    @Query("SELECT a FROM Application a WHERE a.job.employer.id = :employerId")
    Page<Application> findByEmployerId(UUID employerId, Pageable pageable);

    boolean existsByCandidateIdAndJobId(UUID candidateId, UUID jobId);
    boolean existsByCvId(UUID cvId);
}
