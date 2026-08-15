package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record InterviewScheduleRequest(
        @NotNull(message = "Thời gian phỏng vấn không được để trống")
        @Future(message = "Thời gian phỏng vấn phải ở tương lai")
        LocalDateTime scheduledAt,

        String meetingLink,
        String location,
        String note
) {
}
