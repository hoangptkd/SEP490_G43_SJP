package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record InterviewScheduleRequest(
        @NotNull(message = "Thoi gian phong van khong duoc de trong")
        @Future(message = "Thoi gian phong van phai o tuong lai")
        LocalDateTime scheduledAt,

        String meetingLink,
        String location,
        String note
) {
}
