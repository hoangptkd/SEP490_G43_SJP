package com.sjp.recruitment.model.dto.response;

public record TaxCodeLookupResponse(
        String taxCode,
        String companyName,
        String address,
        String status
) {
}
