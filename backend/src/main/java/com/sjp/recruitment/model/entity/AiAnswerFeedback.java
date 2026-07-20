package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_answer_feedbacks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiAnswerFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false, unique = true)
    private InterviewAnswer answer;

    @Column(name = "relevance_score")
    private BigDecimal relevanceScore;

    @Column(name = "clarity_score")
    private BigDecimal clarityScore;

    @Column(name = "depth_score")
    private BigDecimal depthScore;

    @Column(name = "confidence_score")
    private BigDecimal confidenceScore;

    @Column(name = "overall_score")
    private BigDecimal overallScore;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    @Column(columnDefinition = "TEXT")
    private String suggestions;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt = LocalDateTime.now();
}
