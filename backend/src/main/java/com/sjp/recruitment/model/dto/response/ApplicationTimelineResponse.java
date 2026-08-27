package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;

public record ApplicationTimelineResponse(
        String id,
        String fromStatus,
        String toStatus,
        String publicNote,
        LocalDateTime createdAt
) {
}
