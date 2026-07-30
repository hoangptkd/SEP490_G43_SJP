package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentStatusResponse(
        String id,
        String status,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String planName,
        String subscriptionStatus,
        LocalDateTime paidAt,
        String failureReason
) {
}
