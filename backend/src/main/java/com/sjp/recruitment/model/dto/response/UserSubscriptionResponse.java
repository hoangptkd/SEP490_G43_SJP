package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record UserSubscriptionResponse(
        String planId,
        String planName,
        String status,
        BigDecimal price,
        String currency,
        List<String> benefits,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        List<FeatureUsageResponse> usages
) {
}
