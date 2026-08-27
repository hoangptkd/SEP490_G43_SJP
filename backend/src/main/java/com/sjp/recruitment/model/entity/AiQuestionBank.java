package com.sjp.recruitment.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_question_bank")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiQuestionBank {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_set_id", nullable = false)
    private AiQuestionSet questionSet;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "question_type", nullable = false)
    private String questionType;

    private String difficulty;

    @Column(name = "skill_tag")
    private String skillTag;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "time_limit_seconds")
    private Integer timeLimitSeconds;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
