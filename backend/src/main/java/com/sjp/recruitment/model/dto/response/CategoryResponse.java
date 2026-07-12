package com.sjp.recruitment.model.dto.response;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String slug,
        UUID parentId,
        String description,
        String status
) {
}
