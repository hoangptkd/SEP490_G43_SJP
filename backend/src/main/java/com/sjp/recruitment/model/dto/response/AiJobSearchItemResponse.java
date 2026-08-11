package com.sjp.recruitment.model.dto.response;

import java.util.List;

public record AiJobSearchItemResponse(
        int rank,
        JobResponse job,
        int matchScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        String reason
) {
}
