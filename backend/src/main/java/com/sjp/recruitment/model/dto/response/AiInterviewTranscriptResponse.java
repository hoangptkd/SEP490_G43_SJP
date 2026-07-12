package com.sjp.recruitment.model.dto.response;

public record AiInterviewTranscriptResponse(
        String questionId,
        String transcript,
        String transcriptStatus
) {
}
