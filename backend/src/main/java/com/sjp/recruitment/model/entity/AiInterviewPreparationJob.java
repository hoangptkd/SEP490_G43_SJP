package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_interview_preparation_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AiInterviewPreparationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_seeker_id", nullable = false)
    private CandidateProfile candidate;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private InterviewSession session;

    @Column(nullable = false)
    private String status = "QUEUED";

    @Column(nullable = false)
    private String stage = "VALIDATING";

    @Column(nullable = false)
    private Integer progress = 5;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "warning_message", columnDefinition = "TEXT")
    private String warningMessage;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
