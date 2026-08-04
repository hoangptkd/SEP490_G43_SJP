package com.sjp.recruitment.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_ranking_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AiRankingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    // We can map ranking_job_id if we keep it, but it seems we might just bypass it.
    // Let's add it as nullable in case it's still in the DB.
    @Column(name = "ranking_job_id", insertable = false, updatable = false)
    private UUID rankingJobId;

    @Column(name = "rank_position")
    private Integer rankPosition;

    @Column(name = "match_score")
    private BigDecimal matchScore;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_breakdown", columnDefinition = "jsonb", nullable = false)
    private JsonNode scoreBreakdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_requirements", columnDefinition = "jsonb")
    private JsonNode missingRequirements;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "ranked_at")
    private LocalDateTime rankedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
