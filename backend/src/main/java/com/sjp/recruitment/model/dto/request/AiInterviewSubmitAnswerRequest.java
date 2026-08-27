package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiInterviewSubmitAnswerRequest(
        @NotBlank @Size(max = 12_000) String transcript
) {
}
