package com.sjp.recruitment.model.dto.request;

import java.util.UUID;

public record CompanyIndustryRequest(
        UUID categoryId,
        boolean primary
) {
}
