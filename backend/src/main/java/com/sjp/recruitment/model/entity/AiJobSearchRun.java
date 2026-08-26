package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;
import com.sjp.recruitment.model.dto.request.AiJobSearchFilters;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_job_search_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiJobSearchRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_seeker_id", nullable = false)
    private CandidateProfile candidate;

    @Column(nullable = false)
    private String status;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "profile_updated_at")
    private LocalDateTime profileUpdatedAt;

    @Column(name = "cv_id")
    private UUID cvId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "search_filters", columnDefinition = "jsonb", nullable = false)
    private AiJobSearchFilters searchFilters = AiJobSearchFilters.empty();

    @Column(name = "cv_type")
    private String cvType;

    @Column(name = "cv_updated_at")
    private LocalDateTime cvUpdatedAt;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @Column(name = "quota_consumed", nullable = false)
    private boolean quotaConsumed;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
