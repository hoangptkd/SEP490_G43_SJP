package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_session_feedbacks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSessionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private InterviewSession session;

    @Column(name = "overall_score")
    private BigDecimal overallScore;

    @Column(name = "content_score")
    private BigDecimal contentScore;

    @Column(name = "voice_delivery_score")
    private BigDecimal voiceDeliveryScore;

    @Column(name = "raw_voice_delivery_score")
    private BigDecimal rawVoiceDeliveryScore;

    @Column(name = "voice_weight")
    private BigDecimal voiceWeight;

    @Column(name = "replay_count", nullable = false)
    private int replayCount;

    @Column(name = "replay_penalty", nullable = false)
    private BigDecimal replayPenalty = BigDecimal.ZERO;

    @Column(name = "voice_evidence_question_count", nullable = false)
    private int voiceEvidenceQuestionCount;

    @Column(name = "manual_fallback_question_count", nullable = false)
    private int manualFallbackQuestionCount;

    @Column(name = "reference_only", nullable = false)
    private boolean referenceOnly = true;

    @Column(name = "evaluation_profile_version", length = 80)
    private String evaluationProfileVersion;

    @Column(name = "rubric_version", length = 80)
    private String rubricVersion;

    @Column(name = "speech_calibration_version", length = 80)
    private String speechCalibrationVersion;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    @Column(columnDefinition = "TEXT")
    private String suggestions;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "evaluation_source", nullable = false)
    private String evaluationSource = "provider";

    @Column(name = "evaluation_fallback", nullable = false)
    private boolean evaluationFallback = false;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt = LocalDateTime.now();
}
