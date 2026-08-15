package com.sjp.recruitment.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash")
    @JsonIgnore
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    private String phone;

    @Column(name = "avatar_url")
    private String avatarUrl;

    private String gender;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerifiedAt = emailVerified ? LocalDateTime.now() : null;
    }

    public void setRole(UserRole role) {
        this.role = role == null ? null : role.databaseValue;
    }

    public void setStatus(UserStatus status) {
        this.status = status == null ? null : status.databaseValue;
    }

    public UserRole getRoleEnum() {
        return UserRole.fromDatabaseValue(role);
    }

    public UserStatus getStatusEnum() {
        return UserStatus.fromDatabaseValue(status);
    }

    public enum UserRole {
        CANDIDATE("job_seeker"),
        EMPLOYER("employer"),
        ADMIN("admin");

        private final String databaseValue;

        UserRole(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public String databaseValue() {
            return databaseValue;
        }

        public static UserRole fromDatabaseValue(String value) {
            if (value == null) {
                return null;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "job_seeker", "candidate" -> CANDIDATE;
                case "employer" -> EMPLOYER;
                case "admin" -> ADMIN;
                default -> null;
            };
        }
    }

    public enum UserStatus {
        PENDING_VERIFICATION("inactive"),
        ACTIVE("active"),
        SUSPENDED("suspended");

        private final String databaseValue;

        UserStatus(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public String databaseValue() {
            return databaseValue;
        }

        public static UserStatus fromDatabaseValue(String value) {
            if (value == null) {
                return null;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "active" -> ACTIVE;
                case "suspended" -> SUSPENDED;
                default -> PENDING_VERIFICATION;
            };
        }
    }

    public enum AuthProvider {
        LOCAL,
        GOOGLE
    }
}
