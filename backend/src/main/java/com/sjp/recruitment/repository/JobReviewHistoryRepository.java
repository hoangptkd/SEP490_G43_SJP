package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.JobReviewHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobReviewHistoryRepository extends JpaRepository<JobReviewHistory, UUID> {
    List<JobReviewHistory> findByJobIdOrderByReviewedAtDesc(UUID jobId);
    Optional<JobReviewHistory> findFirstByJobIdAndActionOrderByReviewedAtDesc(UUID jobId, String action);
}
