package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "interview_questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "question_type", nullable = false)
    private String questionType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private String difficulty;

    @Column(name = "skill_tag")
    private String skillTag;

    @Column(name = "time_limit_seconds")
    private Integer timeLimitSeconds;

    @Column(name = "replay_count", nullable = false)
    private int replayCount = 0;

    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated = true;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType = "AI_GENERATED";

    @Column(name = "source_id", length = 100)
    private String sourceId;

    @Column(name = "prompt_version", length = 80)
    private String promptVersion;

    @Column(name = "rubric_version", length = 80)
    private String rubricVersion;

    @Column(name = "competency_id", length = 100)
    private String competencyId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rubric_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rubric = new LinkedHashMap<>();
}
