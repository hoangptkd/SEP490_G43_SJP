package com.sjp.recruitment.model.dto.response;

public record AiInterviewQuestionResponse(
        String id,
        int orderIndex,
        String questionType,
        String content,
        String difficulty,
        String skillTag,
        Integer timeLimitSeconds,
        AiInterviewAnswerResponse answer
) {
}
