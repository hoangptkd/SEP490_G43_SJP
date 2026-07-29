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
        String candidateResponse,
        LocalDateTime candidateResponseAt,
        String candidateRescheduleNote,
        String employerRescheduleResponse,
        String employerRescheduleNote,
        LocalDateTime employerRescheduleAt,
        String interviewResult,
        String interviewResultNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
