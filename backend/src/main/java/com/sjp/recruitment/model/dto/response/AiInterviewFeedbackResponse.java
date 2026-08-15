package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AiInterviewFeedbackResponse(
        String id,
        BigDecimal questionScore,
        String evaluationStatus,
        Integer barsLevel,
        String scoreReason,
        String feedback,
        List<String> strengths,
        List<String> weaknesses,
        List<String> suggestions,
        String source,
        boolean fallback
) {
}
