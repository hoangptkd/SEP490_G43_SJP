package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;

public record AdminCategoryResponse(
        String id,
        String name,
        String slug,
        String parentId,
        String description,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
