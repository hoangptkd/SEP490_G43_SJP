package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "interview_answers", uniqueConstraints = {
        @UniqueConstraint(name = "interview_answers_session_question_unique", columnNames = {"session_id", "question_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @Column(name = "transcript_text", columnDefinition = "TEXT")
    private String transcriptText;

    @Column(name = "raw_transcript", columnDefinition = "TEXT")
    private String rawTranscript;

    @Column(name = "final_transcript", columnDefinition = "TEXT")
    private String finalTranscript;

    @Column(name = "transcript_edited", nullable = false)
    private boolean transcriptEdited = false;

    @Column(name = "transcript_edit_count", nullable = false)
    private int transcriptEditCount = 0;

    @Column(name = "conversation_state", nullable = false, length = 40)
    private String conversationState = "LISTENING";

    @Column(name = "original_speech_transcript", columnDefinition = "TEXT")
    private String originalSpeechTranscript;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "speech_analysis_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> speechAnalysisJson = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_summary_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> evidenceSummaryJson = new HashMap<>();

    @Column(name = "active_capture_id")
    private UUID activeCaptureId;

    @Column(name = "active_capture_version")
    private Integer activeCaptureVersion;

    @Column(name = "audio_url")
    private String audioUrl;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "is_skipped", nullable = false)
    private boolean skipped = false;

    @Column(name = "transcript_status", nullable = false)
    private String transcriptStatus = "pending";

    @Column(name = "feedback_status", nullable = false)
    private String feedbackStatus = "pending";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "evaluation_source", nullable = false)
    private String evaluationSource = "provider";

    @Column(name = "evaluation_fallback", nullable = false)
    private boolean evaluationFallback = false;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;
}
