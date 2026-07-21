package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;

public record AdminSubscriptionResponse(
        String id,
        String userId,
        String userEmail,
        String userName,
        String planId,
        String planName,
        String status,
        LocalDateTime startDate,
        LocalDateTime endDate,
        LocalDateTime cancelledAt,
        String cancelledReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
