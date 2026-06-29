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
@Table(name = "resumes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CandidateCv {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "job_seeker_id", nullable = false)
    private CandidateProfile candidate;

    @Column(nullable = false)
    private String title;

    @Column(name = "file_name")
    private String originalFileName;

    @Column(name = "file_url")
    private String storageKey;

    @Column(name = "file_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "is_primary", nullable = false)
    private boolean defaultCv = false;

    @Transient
    private boolean deleted = false;

    @Column(name = "parse_status", nullable = false)
    private String parseStatus = "parsed";

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
