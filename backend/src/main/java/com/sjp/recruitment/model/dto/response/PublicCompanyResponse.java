package com.sjp.recruitment.model.dto.response;

import java.util.List;

public record PublicCompanyResponse(
        String id,
        String name,
        String description,
        String website,
        String industry,
        String location,
        Integer companySize,
        String logoUrl,
        boolean verified,
        List<CompanyLocationResponse> locations,
        PageResponse<JobResponse> openJobs
) {
}
