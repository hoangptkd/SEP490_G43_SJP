package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AiInterviewLiveTranscriptionRequest(
        @Min(8_000) @Max(96_000) int sampleRate
) {
}
