package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.InterviewSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface InterviewScheduleRepository extends JpaRepository<InterviewSchedule, UUID> {
    
    Optional<InterviewSchedule> findByIdAndEmployerId(UUID id, UUID employerId);
    
    Optional<InterviewSchedule> findByIdAndCandidateId(UUID id, UUID candidateId);
    
    List<InterviewSchedule> findByApplicationId(UUID applicationId);
    
    Page<InterviewSchedule> findByEmployerId(UUID employerId, Pageable pageable);
    
    Page<InterviewSchedule> findByCandidateId(UUID candidateId, Pageable pageable);
}
