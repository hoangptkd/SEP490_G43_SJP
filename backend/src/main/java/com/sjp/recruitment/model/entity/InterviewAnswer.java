package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

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
