package com.sjp.recruitment.model.dto.response;

public record AdminCompanyOwnerResponse(
        String employerId,
        String email,
        String fullName,
        String phone,
        String position,
        String verificationStatus
) {
}
