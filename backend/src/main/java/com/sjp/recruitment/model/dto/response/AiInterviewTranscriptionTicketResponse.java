package com.sjp.recruitment.model.dto.response;

public record AiInterviewTranscriptionTicketResponse(
        String provider,
        String websocketPath,
        long expiresAt,
        int finalFlushTimeoutMs
) {
}
