package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CompanyProfileRequest(
        @NotBlank(message = "Tên công ty không được để trống")
        String name,
        String description,
        String website,
        String industry,
        String location,
        Integer companySize,
        String taxCode,
        String logoUrl,
        List<CompanyIndustryRequest> industries
) {
}
