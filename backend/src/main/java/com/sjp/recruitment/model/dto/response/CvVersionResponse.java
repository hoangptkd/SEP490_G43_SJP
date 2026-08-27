package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

public record CvVersionResponse(
        String id,
        String title,
        String templateKey,
        Map<String, Object> snapshot,
        LocalDateTime updatedAt
) {
}
