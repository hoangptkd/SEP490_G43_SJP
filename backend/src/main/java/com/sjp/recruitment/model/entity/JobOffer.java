package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_offers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class JobOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id", nullable = false)
    private Employer employer;

    @Column(name = "position_title", nullable = false)
    private String positionTitle;

    @Column(precision = 14, scale = 2)
    private BigDecimal salary;

    @Column(name = "salary_currency", nullable = false)
    private String salaryCurrency = "VND";

    @Column(name = "salary_type")
    private String salaryType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "benefits", columnDefinition = "TEXT")
    private String benefits;

    @Column(name = "working_location")
    private String workingLocation;

    @Column(name = "offer_letter_url")
    private String offerLetterUrl;

    @Column(nullable = false)
    private String status = "sent";

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "candidate_note", columnDefinition = "TEXT")
    private String candidateNote;

    @Column(name = "employer_note", columnDefinition = "TEXT")
    private String employerNote;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
