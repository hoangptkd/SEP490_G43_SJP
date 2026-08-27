package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminPaymentResponse(
        String id,
        String subscriptionId,
        String userId,
        String userEmail,
        String planName,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String gateway,
        String status,
        String transactionId,
        String failureReason,
        String transferContent,
        String qrUrl,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
}
