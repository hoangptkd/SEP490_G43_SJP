package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record EmployerRescheduleResponseRequest(
        @NotBlank(message = "Phản hồi không được để trống (vd: accept_reschedule, reject_reschedule)")
        String response,

        String note,

        LocalDateTime scheduledAt
) {
}
