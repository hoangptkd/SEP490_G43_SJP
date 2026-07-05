package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;

public record AdminCompanySummaryResponse(
        String id,
        String name,
        String industry,
        String taxCode,
        String verificationStatus,
        String status,
        String ownerEmail,
        String ownerName,
        int documentCount,
        int pendingDocumentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
