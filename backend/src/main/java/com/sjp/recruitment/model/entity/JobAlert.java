package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_alerts")
@Data
@EntityListeners(AuditingEntityListener.class)
public class JobAlert {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_seeker_id", nullable = false)
    private CandidateProfile candidate;

    @Column(nullable = false)
    private String name;
    private String keyword;
    private String location;
    private String category;
    @Column(name = "job_type") private String jobType;
    @Column(name = "work_mode") private String workMode;
    @Column(name = "min_salary") private BigDecimal minSalary;
    @Column(name = "max_salary") private BigDecimal maxSalary;
    @Column(nullable = false) private String frequency = "DAILY";
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "last_run_at") private LocalDateTime lastRunAt;
    @CreatedDate @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate private LocalDateTime updatedAt;
}
