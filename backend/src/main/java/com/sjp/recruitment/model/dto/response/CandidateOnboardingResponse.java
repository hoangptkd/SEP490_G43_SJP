package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CandidateOnboardingResponse(
        List<String> desiredJobTitles,
        BigDecimal expectedSalary,
        String experienceLevel,
        List<String> preferredLocations,
        boolean willingToRelocate,
        String status,
        LocalDateTime completedAt
) {
}
