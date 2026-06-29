package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "application_status_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ApplicationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "old_status")
    private String fromStatus;

    @Column(name = "new_status", nullable = false)
    private String toStatus;

    @ManyToOne
    @JoinColumn(name = "changed_by_user_id")
    private User actorUser;

    @Column(name = "note", columnDefinition = "TEXT")
    private String publicNote;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void setFromStatus(Application.ApplicationStatus status) {
        this.fromStatus = status == null ? null : status.databaseValue();
    }

    public void setToStatus(Application.ApplicationStatus status) {
        this.toStatus = status == null ? null : status.databaseValue();
    }
}
