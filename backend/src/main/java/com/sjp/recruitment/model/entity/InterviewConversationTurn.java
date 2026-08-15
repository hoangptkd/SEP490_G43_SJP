package com.sjp.recruitment.model.entity;

import com.sjp.recruitment.model.enums.InterviewTurnAnswerStatus;
import com.sjp.recruitment.model.enums.InterviewTurnType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "interview_conversation_turns", uniqueConstraints = {
        @UniqueConstraint(name = "interview_conversation_turns_session_sequence_unique",
                columnNames = {"session_id", "sequence_no"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class InterviewConversationTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_item_id")
    private InterviewQuestion assessmentItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_turn_id")
    private InterviewConversationTurn replyToTurn;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "turn_type", nullable = false, length = 32)
    private InterviewTurnType turnType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "candidate_raw_answer", columnDefinition = "TEXT")
    private String candidateRawAnswer;

    @Column(name = "candidate_final_answer", columnDefinition = "TEXT")
    private String candidateFinalAnswer;

    @Column(name = "transcript_edited", nullable = false)
    private boolean transcriptEdited = false;

    @Column(name = "edit_count", nullable = false)
    private int editCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_status", nullable = false, length = 24)
    private InterviewTurnAnswerStatus answerStatus = InterviewTurnAnswerStatus.NOT_REQUIRED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analysis_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> analysisJson = new LinkedHashMap<>();

    @Column(name = "answer_client_id")
    private UUID answerClientId;

    @Column(name = "replay_count", nullable = false)
    private int replayCount = 0;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
