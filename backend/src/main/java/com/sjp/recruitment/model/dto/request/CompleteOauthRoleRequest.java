package com.sjp.recruitment.model.dto.request;

import com.sjp.recruitment.model.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CompleteOauthRoleRequest(
        @NotBlank String token,
        @NotNull User.UserRole role
) {
}
