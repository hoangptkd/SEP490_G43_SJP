package com.sjp.recruitment.model.dto.response;

import java.util.UUID;

public record CompanyIndustryResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String categorySlug,
        boolean primary
) {
}
