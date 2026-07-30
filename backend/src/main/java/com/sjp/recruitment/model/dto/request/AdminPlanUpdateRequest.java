package com.sjp.recruitment.model.dto.request;

import java.math.BigDecimal;

public record AdminPlanUpdateRequest(
        String name,
        String targetRole,
        String description,
        BigDecimal price,
        String currency,
        Integer durationDays,
        String featuresJson,
        String status,
        Integer sortOrder
) {
}
