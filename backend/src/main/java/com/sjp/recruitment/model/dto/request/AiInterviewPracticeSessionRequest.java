package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiInterviewPracticeSessionRequest(
        @NotBlank @Size(max = 120) String targetRole,
        @NotEmpty @Size(max = 12) List<@NotBlank @Size(max = 80) String> skills,
        @Size(max = 36) String jobId
) {
}
