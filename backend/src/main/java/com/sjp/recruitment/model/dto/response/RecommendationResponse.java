package com.sjp.recruitment.model.dto.response;

import java.util.List;

public record RecommendationResponse(
        JobResponse job,
        int matchScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        String reason,
        boolean lowConfidence
) {
}
