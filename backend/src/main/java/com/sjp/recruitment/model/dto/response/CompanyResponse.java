package com.sjp.recruitment.model.dto.response;

public record CompanyResponse(
        String id,
        String name,
        String website,
        String location,
        String logoUrl
) {
}
