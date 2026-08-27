package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;

public record AdminAuditLogResponse(
        String id,
        String actorUserId,
        String actorEmail,
        String action,
        String targetType,
        String targetId,
        String oldValueJson,
        String newValueJson,
        String ipAddress,
        LocalDateTime createdAt
) {
}
