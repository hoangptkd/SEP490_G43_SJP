package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InterviewCandidateResponseRequest(
        @NotBlank(message = "Phan hoi khong duoc de trong")
        @Pattern(regexp = "^(request_reschedule|declined)$", message = "Phan hoi khong hop le")
        String response,

        String rescheduleNote
) {
}
