package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InterviewResultRequest(
        @NotBlank(message = "Kết quả không được để trống")
        @Pattern(regexp = "^(COMPLETED|pass|fail|no_show)$", message = "Dữ liệu không hợp lệ")
        String result,

        String note
) {
}
