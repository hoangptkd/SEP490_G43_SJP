package com.sjp.recruitment.model.dto.response;

public record CompanyLocationResponse(
        String id,
        String branchName,
        String address,
        String city,
        String district,
        String country,
        boolean headquarter
) {
}
