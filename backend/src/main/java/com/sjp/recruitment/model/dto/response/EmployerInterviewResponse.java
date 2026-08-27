package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record EmployerInterviewResponse(
        UUID id,
        UUID applicationId,
        Integer roundNumber,
        String status,
        LocalDateTime scheduledAt,
        String location,
        String meetingLink,
        LocalDateTime viewedAt,
        String candidateRescheduleNote,
        String candidateName,
        String candidatePhone,
        String candidateEmail,
        String jobTitle,
        UUID jobId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
