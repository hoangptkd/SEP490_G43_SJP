package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotNull;

public record ApplicationSubmitRequest(
        @NotNull String jobId,
        String cvId,
        String cvVersionId
) {
}
