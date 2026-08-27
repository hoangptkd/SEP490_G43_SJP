package com.sjp.recruitment.model.dto.request;

public record AdminCategoryRequest(
        String name,
        String slug,
        String description,
        String status
) {
}
