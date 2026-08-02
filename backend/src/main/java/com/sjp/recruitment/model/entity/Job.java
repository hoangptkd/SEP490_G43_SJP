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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne
    @JoinColumn(name = "created_by_employer_id", nullable = false)
    private Employer employer;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "requirements", columnDefinition = "TEXT")
    private String requirementsText;

    @Column(columnDefinition = "TEXT")
    private String benefits;

    @Column(nullable = false)
    private Integer vacancies = 1;

    @Column(name = "working_time")
    private String workingTime;

    @Column(name = "salary_type")
    private String salaryType;

    @Column(name = "salary_min")
    private BigDecimal salaryMin;

    @Column(name = "salary_max")
    private BigDecimal salaryMax;

    // Cột location giữ nguyên để backend cũ hoạt động
    private String location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_location_id")
    private CompanyLocation companyLocation;

    @Column(name = "job_type")
    private String jobType;

    @Column(name = "work_mode")
    private String workMode;

    private String currency = "VND";

    @Column(name = "experience_level")
    private String experienceLevel;

    private String status = "draft";

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Hạn công ty sửa tin sau khi admin thông báo từ báo cáo (3 ngày). */
    @Column(name = "report_fix_deadline")
    private LocalDateTime reportFixDeadline;

    private LocalDate deadline;

    @Column(name = "views_count", nullable = false)
    private Integer viewsCount = 0;

    @OneToMany(mappedBy = "job", fetch = FetchType.LAZY)
    private List<JobSkill> jobSkills = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public List<String> getRequirements() {
        if (requirementsText == null || requirementsText.isBlank()) {
            return List.of();
        }
        return Arrays.stream(requirementsText.split("\\R"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    public void setRequirements(List<String> requirements) {
        this.requirementsText = requirements == null ? null : String.join("\n", requirements);
    }

    public List<String> getSkills() {
        if (jobSkills == null) {
            return List.of();
        }
        return jobSkills.stream()
                .map(JobSkill::getSkill)
                .filter(java.util.Objects::nonNull)
                .map(Skill::getName)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public void setSkills(List<String> ignored) {
        // Skills are stored in job_skills/skills in the final schema.
    }

    public enum JobStatus {
        ACTIVE,
        CLOSED,
        DRAFT,
        EXPIRED,
        ARCHIVED
    }
}
