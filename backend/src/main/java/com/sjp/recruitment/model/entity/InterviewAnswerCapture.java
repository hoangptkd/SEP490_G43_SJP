package com.sjp.recruitment.model.entity;

import com.sjp.recruitment.model.enums.VoiceEvidenceStatus;
import com.sjp.recruitment.model.enums.TranscriptCorrectionStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "interview_answer_captures", uniqueConstraints = {
        @UniqueConstraint(name = "interview_answer_capture_version_unique",
                columnNames = {"answer_id", "capture_id", "capture_version"})
})
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
public class InterviewAnswerCapture {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "answer_id", nullable = false)
    private InterviewAnswer answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_turn_id")
    private InterviewConversationTurn conversationTurn;

    @Column(name = "capture_id", nullable = false)
    private UUID captureId;

    @Column(name = "capture_version", nullable = false)
    private int captureVersion;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(nullable = false)
    private String status = "processing";

    @Column(name = "browser_transcript", columnDefinition = "TEXT")
    private String browserTranscript;

    @Column(name = "gladia_transcript", columnDefinition = "TEXT")
    private String gladiaTranscript;

    @Column(name = "raw_transcript", columnDefinition = "TEXT")
    private String rawTranscript;

    @Column(name = "transcript_status")
    private String transcriptStatus;

    @Column(name = "data_quality")
    private String dataQuality;

    @Enumerated(EnumType.STRING)
    @Column(name = "voice_evidence_status", nullable = false, length = 32)
    private VoiceEvidenceStatus voiceEvidenceStatus = VoiceEvidenceStatus.NOT_REQUIRED;

    @Column(name = "voice_evidence_error_code", length = 100)
    private String voiceEvidenceErrorCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "transcript_correction_status", nullable = false, length = 32)
    private TranscriptCorrectionStatus transcriptCorrectionStatus = TranscriptCorrectionStatus.NOT_REQUIRED;

    @Column(name = "corrected_transcript", columnDefinition = "TEXT")
    private String correctedTranscript;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transcript_correction_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> transcriptCorrectionJson = new HashMap<>();

    @Column(name = "transcript_correction_error_code", length = 100)
    private String transcriptCorrectionErrorCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vad_metrics_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> vadMetricsJson = new HashMap<>();

    @Column(name = "error_code")
    private String errorCode;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
