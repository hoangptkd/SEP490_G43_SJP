package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.JobOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface JobOfferRepository extends JpaRepository<JobOffer, UUID> {

    Optional<JobOffer> findByApplicationId(UUID applicationId);

    List<JobOffer> findByApplicationIdIn(List<UUID> applicationIds);

    Optional<JobOffer> findByIdAndEmployerId(UUID id, UUID employerId);

    java.util.List<JobOffer> findByApplicationJobEmployerIdAndStatus(UUID employerId, String status);

    org.springframework.data.domain.Page<JobOffer> findByApplicationJobEmployerIdAndStatusOrderByCreatedAtDesc(UUID employerId, String status, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT o FROM JobOffer o WHERE o.application.job.employer.id = :employerId AND o.status = :status AND o.application.job.status = :jobStatus ORDER BY o.createdAt DESC")
    org.springframework.data.domain.Page<JobOffer> findByApplicationJobEmployerIdAndStatusAndJobStatusOrderByCreatedAtDesc(@Param("employerId") UUID employerId, @Param("status") String status, @Param("jobStatus") String jobStatus, org.springframework.data.domain.Pageable pageable);

    boolean existsByApplicationId(UUID applicationId);

    long countByApplicationJobEmployerIdAndStatus(UUID employerId, String status);

    @Query("SELECT COUNT(o) FROM JobOffer o WHERE o.application.job.employer.id = :employerId AND o.status IN :statuses AND o.application.status = 'accepted'")
    long countActiveOffersByStatuses(@Param("employerId") UUID employerId, @Param("statuses") List<String> statuses);

    @Query("SELECT COUNT(o) FROM JobOffer o WHERE o.application.job.employer.id = :employerId AND o.status IN :statuses AND o.application.status = 'accepted' AND o.application.job.status = :jobStatus")
    long countActiveOffersByStatusesAndJobStatus(@Param("employerId") UUID employerId, @Param("statuses") List<String> statuses, @Param("jobStatus") String jobStatus);
}
