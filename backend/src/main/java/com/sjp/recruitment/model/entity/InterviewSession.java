package com.sjp.recruitment.model.entity;

import com.sjp.recruitment.model.enums.InterviewDialogueState;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "interview_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_seeker_id", nullable = false)
    private CandidateProfile candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @Column(name = "context_type", nullable = false)
    private String contextType = "practice";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "practice_context_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> practiceContext = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evaluation_profile_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> evaluationProfile = new HashMap<>();

    @Column(nullable = false)
    private String title;

    @Column(name = "session_type", nullable = false)
    private String sessionType = "practice";

    @Column(nullable = false)
    private String status = "created";

    @Enumerated(EnumType.STRING)
    @Column(name = "dialogue_state", length = 32)
    private InterviewDialogueState dialogueState;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_turn_id")
    private InterviewConversationTurn currentTurn;

    @Column(name = "next_turn_sequence", nullable = false)
    private Integer nextTurnSequence = 1;

    @Column(name = "assessment_turn_count", nullable = false)
    private Integer assessmentTurnCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_summary_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> evidenceSummaryJson = new HashMap<>();

    @Column(name = "dialogue_version", nullable = false)
    private Integer dialogueVersion = 1;

    @Column(name = "last_error_stage", length = 64)
    private String lastErrorStage;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions = 0;

    @Column(name = "target_question_count", nullable = false)
    private Integer targetQuestionCount = 5;

    @Column(name = "overall_score")
    private BigDecimal overallScore;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public boolean isCompleted() {
        return "completed".equals(status);
    }

    public int effectiveTargetQuestionCount() {
        return targetQuestionCount == null ? 5 : targetQuestionCount;
    }
}
