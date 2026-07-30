package com.sjp.recruitment.model.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminJobReportResponse(
        String id,
        String jobId,
        String jobTitle,
        String companyName,
        String jobStatus,
        String reporterUserId,
        String reporterEmail,
        String reporterName,
        String reporterPhone,
        LocalDate reporterDateOfBirth,
        Integer reporterAge,
        String reason,
        String description,
        String status,
        String adminNote,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
}
