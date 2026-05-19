package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "candidate_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CandidateProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(name = "full_name")
    private String fullName;

    private String bio;

    @Column(columnDefinition = "JSONB")
    private List<String> skills;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(columnDefinition = "JSONB")
    private List<Education> education;

    @Column(columnDefinition = "JSONB")
    private List<WorkExperience> workExperience;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Embedded
    private LocalDateTime updatedAt;

    @Data
    @Embeddable
    public static class Education {
        private String institution;
        private String degree;
        private String field;
        private String startDate;
        private String endDate;
    }

    @Data
    @Embeddable
    public static class WorkExperience {
        private String company;
        private String position;
        private String startDate;
        private String endDate;
        private String description;
    }
}