package com.sjp.recruitment.model.dto.response;

import java.util.List;

public record CompanyProfileResponse(
        String id,
        String name,
        String description,
        String website,
        String industry,
        String location,
        Integer companySize,
        String taxCode,
        boolean verified,
        String status,
        List<CompanyLocationResponse> locations
) {
}
