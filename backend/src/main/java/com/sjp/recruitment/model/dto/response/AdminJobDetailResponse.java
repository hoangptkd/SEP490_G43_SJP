package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;

public record AdminJobDetailResponse(
        JobResponse job,
        String employerEmail,
        String employerName,
        String employerPosition,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
