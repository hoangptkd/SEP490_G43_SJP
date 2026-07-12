package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;

public record CompanyDocumentResponse(
        String id,
        String fileName,
        String fileUrl,
        String fileType,
        String status,
        String rejectReason,
        LocalDateTime uploadedAt,
        LocalDateTime reviewedAt
) {
}
