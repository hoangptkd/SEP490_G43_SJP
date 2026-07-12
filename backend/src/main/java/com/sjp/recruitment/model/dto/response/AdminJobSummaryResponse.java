package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminJobSummaryResponse(
        String id,
        String title,
        String companyName,
        String employerEmail,
        String employerName,
        String status,
        String location,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
