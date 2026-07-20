package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AiInterviewSessionSummaryResponse(
        BigDecimal overallScore,
        String summary,
        List<String> strengths,
        List<String> weaknesses,
        List<String> improvementPlan,
        String source,
        boolean fallback
) {
}
