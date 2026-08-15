package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.InterviewSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface InterviewScheduleRepository extends JpaRepository<InterviewSchedule, UUID> {

    Optional<InterviewSchedule> findByIdAndEmployerId(UUID id, UUID employerId);

    Optional<InterviewSchedule> findByIdAndCandidateId(UUID id, UUID candidateId);

    List<InterviewSchedule> findByApplicationId(UUID applicationId);

    List<InterviewSchedule> findByApplicationIdIn(List<UUID> applicationIds);

    Page<InterviewSchedule> findByEmployerId(UUID employerId, Pageable pageable);

    List<InterviewSchedule> findByEmployerIdAndStatus(UUID employerId, String status);

    Page<InterviewSchedule> findByEmployerIdAndStatusOrderByScheduledAtDesc(UUID employerId, String status, Pageable pageable);

    @Query("SELECT i FROM InterviewSchedule i WHERE i.employer.id = :employerId AND i.status = :status AND i.application.job.status = :jobStatus ORDER BY i.scheduledAt DESC")
    Page<InterviewSchedule> findByEmployerIdAndStatusAndJobStatusOrderByScheduledAtDesc(@Param("employerId") UUID employerId, @Param("status") String status, @Param("jobStatus") String jobStatus, Pageable pageable);

    @Query("SELECT i FROM InterviewSchedule i WHERE i.employer.id = :employerId AND i.status = 'COMPLETED' AND NOT EXISTS (SELECT o FROM JobOffer o WHERE o.application = i.application) ORDER BY i.scheduledAt DESC")
    Page<InterviewSchedule> findCompletedInterviewsWithoutOffer(@Param("employerId") UUID employerId, Pageable pageable);

    @Query("SELECT i FROM InterviewSchedule i WHERE i.employer.id = :employerId AND i.status = 'COMPLETED' AND NOT EXISTS (SELECT o FROM JobOffer o WHERE o.application = i.application) AND i.application.job.status = :jobStatus ORDER BY i.scheduledAt DESC")
    Page<InterviewSchedule> findCompletedInterviewsWithoutOfferAndJobStatus(@Param("employerId") UUID employerId, @Param("jobStatus") String jobStatus, Pageable pageable);

    Page<InterviewSchedule> findByCandidateId(UUID candidateId, Pageable pageable);

    List<InterviewSchedule> findByEmployerIdAndScheduledAtBetweenOrderByScheduledAtAsc(UUID employerId, java.time.LocalDateTime start, java.time.LocalDateTime end);

    List<InterviewSchedule> findByEmployerIdAndScheduledAtAfterOrderByScheduledAtAsc(UUID employerId, java.time.LocalDateTime start);

    @Query("SELECT i FROM InterviewSchedule i WHERE i.employer.id = :employerId AND i.scheduledAt > :start AND i.application.job.status = :jobStatus ORDER BY i.scheduledAt ASC")
    List<InterviewSchedule> findByEmployerIdAndScheduledAtAfterAndJobStatusOrderByScheduledAtAsc(@Param("employerId") UUID employerId, @Param("start") java.time.LocalDateTime start, @Param("jobStatus") String jobStatus);

    @Query("SELECT COUNT(s) > 0 FROM InterviewSchedule s " +
           "WHERE s.application.id IN :applicationIds " +
           "AND UPPER(s.status) IN ('PENDING_RESPONSE', 'ACCEPTED', 'RESCHEDULE_REQUESTED', 'SCHEDULED', 'RESCHEDULED')")
    boolean existsActiveByApplicationIds(@Param("applicationIds") List<UUID> applicationIds);

    long countByEmployerIdAndStatus(UUID employerId, String status);

    @Query("SELECT COUNT(i) FROM InterviewSchedule i WHERE i.employer.id = :employerId AND i.status = :status AND i.application.job.status = :jobStatus")
    long countByEmployerIdAndStatusAndJobStatus(@Param("employerId") UUID employerId, @Param("status") String status, @Param("jobStatus") String jobStatus);

    @Query("SELECT COUNT(i) FROM InterviewSchedule i WHERE i.employer.id = :employerId AND i.status IN :statuses AND i.application.status = 'interview_scheduled'")
    long countActiveInterviewsByStatuses(@Param("employerId") UUID employerId, @Param("statuses") List<String> statuses);

    @Query("SELECT COUNT(i) FROM InterviewSchedule i WHERE i.employer.id = :employerId AND i.status IN :statuses AND i.application.status = 'interview_scheduled' AND i.application.job.status = :jobStatus")
    long countActiveInterviewsByStatusesAndJobStatus(@Param("employerId") UUID employerId, @Param("statuses") List<String> statuses, @Param("jobStatus") String jobStatus);
}
