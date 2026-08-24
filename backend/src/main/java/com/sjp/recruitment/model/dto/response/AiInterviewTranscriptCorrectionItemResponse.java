package com.sjp.recruitment.model.dto.response;

public record AiInterviewTranscriptCorrectionItemResponse(
        String original,
        String replacement,
        double confidence,
        String reason
) {
}
