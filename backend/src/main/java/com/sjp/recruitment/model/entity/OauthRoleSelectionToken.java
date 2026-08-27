package com.sjp.recruitment.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OauthRoleSelectionToken {

    private UUID id = UUID.randomUUID();
    private String email;
    private String provider;
    private String providerId;
    private String fullName;
    private String token;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt = LocalDateTime.now();
}
