package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record OccupiedInterviewSlotResponse(
        UUID id,
        UUID applicationId,
        String candidateName,
        LocalDateTime scheduledAt,
        String status
) {
}
