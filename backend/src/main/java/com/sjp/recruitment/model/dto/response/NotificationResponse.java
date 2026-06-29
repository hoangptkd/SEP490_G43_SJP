package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;

public record NotificationResponse(
        String id,
        String type,
        String title,
        String message,
        boolean read,
        String relatedEntityType,
        String relatedEntityId,
        LocalDateTime createdAt
) {
}
