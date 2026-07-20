package com.sjp.recruitment.model.dto.response;

public record AiInterviewAnswerResponse(
        String id,
        String questionId,
        String transcript,
        boolean skipped,
        String transcriptStatus,
        String feedbackStatus,
        String errorMessage,
        String answeredAt,
        AiInterviewFeedbackResponse feedback
) {
}
