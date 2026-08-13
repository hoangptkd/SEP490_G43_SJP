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

    @Query("SELECT i FROM InterviewSchedule i WHERE i.employer.id = :employerId AND i.status = 'COMPLETED' AND NOT EXISTS (SELECT o FROM JobOffer o WHERE o.application = i.application) ORDER BY i.scheduledAt DESC")
    Page<InterviewSchedule> findCompletedInterviewsWithoutOffer(@Param("employerId") UUID employerId, Pageable pageable);

    Page<InterviewSchedule> findByCandidateId(UUID candidateId, Pageable pageable);

    List<InterviewSchedule> findByEmployerIdAndScheduledAtBetweenOrderByScheduledAtAsc(UUID employerId, java.time.LocalDateTime start, java.time.LocalDateTime end);

    List<InterviewSchedule> findByEmployerIdAndScheduledAtAfterOrderByScheduledAtAsc(UUID employerId, java.time.LocalDateTime start);

    @Query("SELECT COUNT(s) > 0 FROM InterviewSchedule s " +
           "WHERE s.application.id IN :applicationIds " +
           "AND s.status IN ('scheduled', 'rescheduled')")
    boolean existsActiveByApplicationIds(@Param("applicationIds") List<UUID> applicationIds);
}
