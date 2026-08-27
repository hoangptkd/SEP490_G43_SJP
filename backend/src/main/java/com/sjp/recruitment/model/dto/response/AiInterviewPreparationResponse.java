package com.sjp.recruitment.model.dto.response;

public record AiInterviewPreparationResponse(
        String id,
        String status,
        String stage,
        int progress,
        String message,
        String warningMessage,
        String sessionId,
        String errorCode,
        String errorMessage,
        String createdAt,
        String updatedAt
) {
}
