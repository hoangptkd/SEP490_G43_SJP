package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobReportRequest(
        @NotBlank @Size(max = 50) String reason,
        @Size(max = 2000) String description
) {
}
