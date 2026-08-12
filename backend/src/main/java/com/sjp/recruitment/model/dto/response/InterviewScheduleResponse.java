package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record InterviewScheduleResponse(
        UUID id,
        UUID applicationId,
        Integer roundNumber,
        LocalDateTime scheduledAt,
        String meetingLink,
        String location,
        String status,
        String note,
        LocalDateTime viewedAt,
        LocalDateTime respondedAt,
        LocalDateTime responseDeadline,
        LocalDateTime lastReminderAt,
        String candidateRescheduleNote,
        String employerRescheduleResponse,
        String employerRescheduleNote,
        LocalDateTime employerRescheduleAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
