package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated = true;
}
