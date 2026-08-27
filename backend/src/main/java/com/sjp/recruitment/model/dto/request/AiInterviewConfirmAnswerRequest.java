package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiInterviewConfirmAnswerRequest(
        @Size(max = 12_000) String rawTranscript,
        @NotBlank @Size(max = 12_000) String finalTranscript
) {
}
