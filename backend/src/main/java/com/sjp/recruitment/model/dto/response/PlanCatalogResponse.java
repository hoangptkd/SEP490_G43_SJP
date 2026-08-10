package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record PlanCatalogResponse(
        String id,
        String name,
        String targetRole,
        String description,
        BigDecimal price,
        String currency,
        int durationDays,
        List<String> benefits,
        int sortOrder,
        Integer maxJobs,
        Integer maxCv,
        Integer maxApplicationsPerDay,
        Integer maxAiSessionsPerDay,
        Integer listingPriority
) {
}
