package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CompanyLocationRequest(
        @NotBlank(message = "Tên chi nhánh không được để trống")
        String branchName,
        String address,
        String city,
        String district,
        String country,
        boolean headquarter
) {
}
