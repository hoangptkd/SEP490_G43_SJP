package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InterviewResultRequest(
        @NotBlank(message = "Ket qua khong duoc de trong")
        @Pattern(regexp = "^(pass|fail|no_show)$", message = "Ket qua khong hop le")
        String result,

        String note
) {
}
