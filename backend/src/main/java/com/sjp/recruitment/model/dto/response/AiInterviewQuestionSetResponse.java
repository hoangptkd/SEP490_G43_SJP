package com.sjp.recruitment.model.dto.response;

public record AiInterviewQuestionSetResponse(
        String id,
        String code,
        String title,
        String description,
        String targetRole,
        long questionCount
) {
}
