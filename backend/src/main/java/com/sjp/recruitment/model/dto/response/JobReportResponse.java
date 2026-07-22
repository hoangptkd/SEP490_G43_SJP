package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;

public record JobReportResponse(
        String id,
        String jobId,
        String reason,
        String description,
        String status,
        LocalDateTime createdAt
) {
}
