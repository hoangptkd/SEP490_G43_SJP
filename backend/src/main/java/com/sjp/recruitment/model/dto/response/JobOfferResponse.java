package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record JobOfferResponse(
        UUID id,
        UUID applicationId,
        String positionTitle,
        BigDecimal salary,
        String salaryCurrency,
        String salaryType,
        LocalDate startDate,
        String benefits,
        String workingLocation,
        String offerLetterUrl,
        String status,
        LocalDateTime sentAt,
        LocalDateTime respondedAt,
        LocalDateTime expiresAt,
        String candidateNote,
        String employerNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
