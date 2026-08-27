package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;

public record AdminUserSummaryResponse(
        String id,
        String email,
        String fullName,
        String phone,
        String role,
        String status,
        boolean emailVerified,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
