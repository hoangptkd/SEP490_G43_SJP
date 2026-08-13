package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    Page<Application> findByCandidateId(UUID candidateId, Pageable pageable);
    List<Application> findByNeedRerankTrue();
    Page<Application> findByCandidateUserId(UUID userId, Pageable pageable);
    Page<Application> findByJobId(UUID jobId, Pageable pageable);
    List<Application> findAllByJobId(UUID jobId);
    long countByJobId(UUID jobId);

    @Query("SELECT a.job.id, COUNT(a) FROM Application a WHERE a.job.id IN :jobIds GROUP BY a.job.id")
    List<Object[]> countByJobIdIn(@Param("jobIds") List<UUID> jobIds);
    long countByJobIdAndStatus(UUID jobId, String status);

    long countByJobEmployerId(UUID employerId);
    long countByJobEmployerIdAndStatus(UUID employerId, String status);
    
    List<Application> findByJobEmployerIdAndStatus(UUID employerId, String status);
    
    Page<Application> findByJobEmployerIdAndStatusOrderBySubmittedAtDesc(UUID employerId, String status, Pageable pageable);
    
    @Query("SELECT a FROM Application a WHERE a.job.employer.id = :employerId AND a.submittedAt >= :startDate")
    List<Application> findApplicationsByEmployerSince(@Param("employerId") UUID employerId, @Param("startDate") java.time.LocalDateTime startDate);

    @Query("SELECT a.id, a.submittedAt FROM Application a WHERE a.job.employer.id = :employerId AND a.submittedAt >= :startDate")
    List<Object[]> findApplicationDatesByEmployerSince(@Param("employerId") UUID employerId, @Param("startDate") java.time.LocalDateTime startDate);

    @Query("SELECT a.status, COUNT(a) FROM Application a WHERE a.job.employer.id = :employerId GROUP BY a.status")
    List<Object[]> countApplicationsByStatusForEmployer(@Param("employerId") UUID employerId);

    @Query("SELECT a FROM Application a WHERE a.job.employer.id = :employerId")
    Page<Application> findByEmployerId(UUID employerId, Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Application a SET a.needRerank = true WHERE a.job.id = :jobId AND a.status IN ('applied', 'reviewed', 'shortlisted')")
    void markApplicationsForRerank(UUID jobId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Application a SET a.needRerank = true WHERE a.cv.id = :cvId AND a.status IN ('applied', 'reviewed', 'shortlisted')")
    void markApplicationsForRerankByCvId(UUID cvId);

    @Query("SELECT a FROM Application a WHERE a.job.company.id = :companyId ORDER BY a.submittedAt DESC")
    List<Application> findByCompanyId(@Param("companyId") UUID companyId);

    @Query("SELECT a FROM Application a WHERE a.job.id = :jobId ORDER BY a.submittedAt DESC")
    List<Application> findByJobIdOrderBySubmittedAtDesc(@Param("jobId") UUID jobId);

    boolean existsByCandidateIdAndJobId(UUID candidateId, UUID jobId);
    boolean existsByCvId(UUID cvId);

    @Query("SELECT a.job.id FROM Application a WHERE a.candidate.id = :candidateId AND a.job.id IN :jobIds")
    List<UUID> findAppliedJobIds(@Param("candidateId") UUID candidateId, @Param("jobIds") List<UUID> jobIds);

    @Query("SELECT a.job.id, COUNT(a) FROM Application a WHERE a.job.id IN :jobIds GROUP BY a.job.id")
    List<Object[]> countByJobIds(@Param("jobIds") List<UUID> jobIds);
}
