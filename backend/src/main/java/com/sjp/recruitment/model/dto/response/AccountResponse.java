package com.sjp.recruitment.model.dto.response;

public record AccountResponse(
        String id,
        String email,
        String role,
        String status,
        boolean emailVerified,
        String fullName,
        String phone,
        String avatarUrl,
        boolean passwordLoginEnabled
) {
}
