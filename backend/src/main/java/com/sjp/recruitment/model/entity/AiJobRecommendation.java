package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_job_recommendations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiJobRecommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_seeker_id", nullable = false)
    private CandidateProfile candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private AiJobSearchRun run;

    @Column(name = "match_score", precision = 5, scale = 2)
    private BigDecimal matchScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> reasonJson = Map.of();

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "rank_position")
    private Integer rankPosition;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "is_viewed", nullable = false)
    private boolean viewed;
}
