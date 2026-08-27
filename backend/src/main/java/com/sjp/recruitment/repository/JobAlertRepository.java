package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.JobAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobAlertRepository extends JpaRepository<JobAlert, UUID> {
    Page<JobAlert> findByCandidateId(UUID candidateId, Pageable pageable);
    Optional<JobAlert> findByIdAndCandidateId(UUID id, UUID candidateId);
    List<JobAlert> findByEnabledTrue();
}
