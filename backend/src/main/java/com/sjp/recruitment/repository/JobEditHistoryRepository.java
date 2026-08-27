package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.JobEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface JobEditHistoryRepository extends JpaRepository<JobEditHistory, UUID> {
    List<JobEditHistory> findByJobIdOrderByCreatedAtDesc(UUID jobId);
}
