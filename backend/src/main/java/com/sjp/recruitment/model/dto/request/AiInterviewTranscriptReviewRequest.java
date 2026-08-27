package com.sjp.recruitment.model.dto.request;

import com.sjp.recruitment.model.enums.TranscriptReviewAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AiInterviewTranscriptReviewRequest(
        @NotNull TranscriptReviewAction action,
        String captureId,
        @Positive Integer captureVersion,
        @Size(max = 12_000) String transcript,
        @PositiveOrZero int expectedEditCount
) {
}
