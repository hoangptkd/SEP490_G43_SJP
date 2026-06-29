package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SubscriptionResponse(
        String planCode,
        String planName,
        String status,
        BigDecimal price,
        List<String> benefits,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        long savedJobsCount,
        long cvCount,
        long unreadNotificationsCount
) {
}
