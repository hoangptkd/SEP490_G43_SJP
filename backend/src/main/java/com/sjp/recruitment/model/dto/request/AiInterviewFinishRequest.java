package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.Size;

public record AiInterviewFinishRequest(
        @Size(max = 36) String questionId,
        @Size(max = 12_000) String transcript
) {
}
