package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "job_seekers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CandidateProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    private String headline;

    @Column(name = "summary")
    private String bio;

    private String location;

    @Column(name = "years_of_experience", nullable = false)
    private Integer experienceYears = 0;

    @Column(name = "experience_level")
    private String experienceLevel;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "portfolio_url")
    private String portfolioUrl;

    @OneToMany(mappedBy = "candidate", fetch = FetchType.LAZY)
    private List<CandidateSkill> candidateSkills = new ArrayList<>();

    @Transient
    private List<String> skills;

    @Transient
    private List<Object> education = List.of();

    @Transient
    private List<Object> workExperience = List.of();

    @Transient
    private List<Object> projects = List.of();

    @Transient
    private List<Object> certifications = List.of();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public String getFullName() {
        return user == null ? null : user.getFullName();
    }

    public void setFullName(String fullName) {
        if (user != null) {
            user.setFullName(fullName);
        }
    }

    public String getPhone() {
        return user == null ? null : user.getPhone();
    }

    public void setPhone(String phone) {
        if (user != null) {
            user.setPhone(phone);
        }
    }

    public List<String> getSkills() {
        if (skills != null) {
            return skills;
        }
        if (candidateSkills == null) {
            return List.of();
        }
        return candidateSkills.stream()
                .map(CandidateSkill::getSkill)
                .filter(java.util.Objects::nonNull)
                .map(Skill::getName)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
