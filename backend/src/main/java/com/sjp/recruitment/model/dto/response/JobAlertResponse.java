package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record JobAlertResponse(
        String id, String name, String keyword, String location, String category,
        String jobType, String workMode, BigDecimal minSalary, BigDecimal maxSalary,
        String frequency, boolean enabled, LocalDateTime lastRunAt, LocalDateTime createdAt
) {
}
