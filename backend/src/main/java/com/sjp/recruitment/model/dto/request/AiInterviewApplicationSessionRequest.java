package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiInterviewApplicationSessionRequest(
        @NotBlank @Size(max = 36) String applicationId
) {
}
