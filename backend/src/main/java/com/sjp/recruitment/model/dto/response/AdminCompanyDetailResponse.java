package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record AdminCompanyDetailResponse(
        CompanyProfileResponse company,
        List<CompanyDocumentResponse> documents,
        AdminCompanyOwnerResponse owner,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
