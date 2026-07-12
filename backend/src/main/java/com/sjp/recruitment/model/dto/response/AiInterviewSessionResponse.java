package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AiInterviewSessionResponse(
        String id,
        String title,
        String contextType,
        String status,
        int totalQuestions,
        BigDecimal overallScore,
        String applicationId,
        JobResponse job,
        Map<String, Object> practiceContext,
        String startedAt,
        String completedAt,
        String createdAt,
        String updatedAt,
        List<AiInterviewQuestionResponse> questions,
        AiInterviewSessionSummaryResponse summary
) {
}
