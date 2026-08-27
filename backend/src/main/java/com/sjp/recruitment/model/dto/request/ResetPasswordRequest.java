package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank
        String token,

        @NotBlank
        String password
) {
}
