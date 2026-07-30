package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminRevenueSummaryResponse(
        BigDecimal totalPaid,
        BigDecimal totalPending,
        BigDecimal totalRefunded,
        long paidCount,
        long pendingCount,
        long failedCount,
        long activeSubscriptions,
        long activePlans,
        LocalDateTime updatedAt
) {
}
