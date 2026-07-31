package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "interview_schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class InterviewSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id", nullable = false)
    private Employer employer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_seeker_id", nullable = false)
    private CandidateProfile candidate;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber = 1;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "meeting_link")
    private String meetingLink;

    private String location;

    @Column(nullable = false)
    private String status = "scheduled";

    private String note;

    @Column(name = "candidate_response")
    private String candidateResponse = "pending";

    @Column(name = "candidate_response_at")
    private LocalDateTime candidateResponseAt;

    @Column(name = "candidate_reschedule_note")
    private String candidateRescheduleNote;

    @Column(name = "employer_reschedule_response")
    private String employerRescheduleResponse;

    @Column(name = "employer_reschedule_note")
    private String employerRescheduleNote;

    @Column(name = "employer_reschedule_at")
    private LocalDateTime employerRescheduleAt;

    @Column(name = "interview_result")
    private String interviewResult = "pending";

    @Column(name = "interview_result_note")
    private String interviewResultNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_updated_by")
    private User resultUpdatedBy;

    @Column(name = "result_updated_at")
    private LocalDateTime resultUpdatedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
