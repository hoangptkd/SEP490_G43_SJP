package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminPlanResponse(
        String id,
        String name,
        String targetRole,
        String description,
        BigDecimal price,
        String currency,
        Integer durationDays,
        String featuresJson,
        String status,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
