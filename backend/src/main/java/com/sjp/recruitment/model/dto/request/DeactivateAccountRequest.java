package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeactivateAccountRequest(
        @NotBlank
        String currentPassword
) {
}
