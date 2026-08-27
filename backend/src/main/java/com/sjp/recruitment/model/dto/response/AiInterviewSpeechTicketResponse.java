package com.sjp.recruitment.model.dto.response;

public record AiInterviewSpeechTicketResponse(
        String streamUrl,
        String contentType,
        long expiresAt
) {
}
