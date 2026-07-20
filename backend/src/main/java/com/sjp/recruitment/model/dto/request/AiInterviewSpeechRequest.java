package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiInterviewSpeechRequest(
        @NotBlank @Size(max = 2_000) String input
) {
}
