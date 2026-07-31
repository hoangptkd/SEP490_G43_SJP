package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankTransferInfo(
        String paymentId,
        String orderCode,
        String planName,
        String bankName,
        String bankCode,
        String accountNumber,
        String accountName,
        String branch,
        String transferContent,
        BigDecimal amount,
        String currency,
        String qrUrl,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        String status
) {
}
