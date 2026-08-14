package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InterviewCandidateResponseRequest(
        @NotBlank(message = "Phản hồi không được để trống")
        @Pattern(regexp = "^(confirmed|request_reschedule|declined)$", message = "Phản hồi không hợp lệ")
        String response,

        String rescheduleNote
) {
}
