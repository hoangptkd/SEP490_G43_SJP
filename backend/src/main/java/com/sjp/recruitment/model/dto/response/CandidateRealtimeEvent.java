package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record CandidateRealtimeEvent(
        UUID eventId,
        String type,
        UUID entityId,
        LocalDateTime occurredAt,
        long version
) {
}
