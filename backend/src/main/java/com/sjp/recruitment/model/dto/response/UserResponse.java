package com.sjp.recruitment.model.dto.response;

public record UserResponse(
        String id,
        String email,
        String role,
        String status,
        boolean emailVerified
) {
}
