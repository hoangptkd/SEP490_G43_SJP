package com.sjp.recruitment.model.dto.response;

public record AiInterviewQuestionResponse(
        String id,
        int orderIndex,
        String questionType,
        String content,
        String difficulty,
        String skillTag,
        Integer timeLimitSeconds,
        int replayCount,
        String sourceType,
        String sourceId,
        String promptVersion,
        String rubricVersion,
        String competencyId,
        AiInterviewAnswerResponse answer
) {
}
