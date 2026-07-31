package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(nullable = false)
    private String status = "active";

    @CreatedDate
    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "end_date")
    private LocalDateTime expiresAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_reason")
    private String cancelledReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public void setStatus(SubscriptionStatus status) {
        this.status = status == null ? null : status.databaseValue;
    }

    public SubscriptionStatus getStatusEnum() {
        return SubscriptionStatus.fromDatabaseValue(status);
    }

    public enum SubscriptionStatus {
        PENDING("pending"),
        ACTIVE("active"),
        EXPIRED("expired"),
        CANCELLED("cancelled");

        private final String databaseValue;

        SubscriptionStatus(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public String databaseValue() {
            return databaseValue;
        }

        public static SubscriptionStatus fromDatabaseValue(String value) {
            if (value == null) {
                return null;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "pending" -> PENDING;
                case "expired" -> EXPIRED;
                case "cancelled" -> CANCELLED;
                default -> ACTIVE;
            };
        }
    }
}
