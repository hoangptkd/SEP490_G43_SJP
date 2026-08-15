package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiInterviewPracticeSessionRequest(
        @NotBlank @Size(max = 36) String cvId,
        @NotBlank @Size(max = 120) String targetRole,
        @NotBlank
        @Pattern(regexp = "intern|fresher|junior|middle|senior")
        String seniority,
        @Size(max = 3) List<@NotBlank @Size(max = 80) String> focusSkills
) {
}
