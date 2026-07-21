package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminPaymentResponse(
        String id,
        String subscriptionId,
        String userId,
        String userEmail,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String gateway,
        String status,
        String transactionId,
        String failureReason,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {
}
