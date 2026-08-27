package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;

public record AdminSettingResponse(
        String key,
        String value,
        String description,
        LocalDateTime updatedAt
) {
}
