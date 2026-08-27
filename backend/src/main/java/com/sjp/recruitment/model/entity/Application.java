package com.sjp.recruitment.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.model.dto.JobSnapshot;
import com.sjp.recruitment.model.dto.SubmittedResumeSnapshot;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Application {

    private static final ObjectMapper SNAPSHOT_OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne
    @JoinColumn(name = "job_seeker_id", nullable = false)
    private CandidateProfile candidate;

    @ManyToOne
    @JoinColumn(name = "resume_id")
    private CandidateCv cv;

    @ManyToOne
    @JoinColumn(name = "resume_id", insertable = false, updatable = false)
    private CvVersion cvVersion;

    @Column(nullable = false)
    private String status = "applied";

    @CreatedDate
    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime reviewedAt;

    @Column(name = "ai_match_score")
    private Integer aiMatchScore;

    @Column(name = "ai_match_analysis")
    private String aiMatchAnalysis;

    @Column(name = "preferred_location")
    private String preferredLocation;

    @Column(name = "cover_letter")
    private String coverLetter;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resume_snapshot_json", columnDefinition = "jsonb")
    private JsonNode resumeSnapshotJson;

    @Column(name = "resume_file_url_snapshot")
    private String resumeFileStorageKeySnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "job_snapshot_json", columnDefinition = "jsonb")
    private JsonNode jobSnapshotJson;

    @Column(name = "need_rerank")
    private Boolean needRerank = false;

    public JobSnapshot getJobSnapshotJson() {
        if (jobSnapshotJson == null || jobSnapshotJson.isNull()) {
            return null;
        }
        try {
            if (jobSnapshotJson.isTextual()) {
                String raw = jobSnapshotJson.asText();
                if (raw == null || raw.isBlank()) {
                    return null;
                }
                return SNAPSHOT_OBJECT_MAPPER.readValue(raw, JobSnapshot.class);
            }
            return SNAPSHOT_OBJECT_MAPPER.treeToValue(jobSnapshotJson, JobSnapshot.class);
        } catch (Exception exception) {
            return null;
        }
    }

    public void setJobSnapshotJson(JobSnapshot snapshot) {
        this.jobSnapshotJson = snapshot == null ? null : SNAPSHOT_OBJECT_MAPPER.valueToTree(snapshot);
    }

    public SubmittedResumeSnapshot getResumeSnapshot() {
        if (resumeSnapshotJson == null || resumeSnapshotJson.isNull()) {
            return null;
        }
        try {
            return SNAPSHOT_OBJECT_MAPPER.treeToValue(resumeSnapshotJson, SubmittedResumeSnapshot.class);
        } catch (Exception exception) {
            return null;
        }
    }

    public void setResumeSnapshot(SubmittedResumeSnapshot snapshot) {
        this.resumeSnapshotJson = snapshot == null ? null : SNAPSHOT_OBJECT_MAPPER.valueToTree(snapshot);
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status == null ? null : status.databaseValue;
    }

    public ApplicationStatus getStatusEnum() {
        return ApplicationStatus.fromDatabaseValue(status);
    }

    public enum ApplicationStatus {
        SUBMITTED("applied"),
        UNDER_REVIEW("reviewed"),
        SHORTLISTED("shortlisted"),
        INTERVIEW_SCHEDULED("interview_scheduled"),
        ACCEPTED("accepted"),
        HIRED("hired"),
        REJECTED("rejected"),
        WITHDRAWN("withdrawn");

        private final String databaseValue;

        ApplicationStatus(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public String databaseValue() {
            return databaseValue;
        }

        public static ApplicationStatus fromDatabaseValue(String value) {
            if (value == null) {
                return null;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "applied" -> SUBMITTED;
                case "reviewed" -> UNDER_REVIEW;
                case "shortlisted" -> SHORTLISTED;
                case "interview_scheduled" -> INTERVIEW_SCHEDULED;
                case "accepted" -> ACCEPTED;
                case "hired" -> HIRED;
                case "rejected" -> REJECTED;
                case "withdrawn" -> WITHDRAWN;
                default -> SUBMITTED;
            };
        }
    }
}
