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
        @jakarta.validation.constraints.Pattern(
                regexp = "^(|(0|\\+84)[35789][0-9]{8})$",
                message = "Số điện thoại không hợp lệ (Phải đúng định dạng số điện thoại Việt Nam)"
        )
        String contactPhone,
        String contactEmail,
        List<CompanyIndustryRequest> industries,
        Boolean submitForReview
) {
}
