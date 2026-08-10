package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplicationSubmitRequest(
        @NotBlank @Size(max = 36) String jobId,
        @Size(max = 36) String cvId,
        @Size(max = 36) String cvVersionId,
        String preferredLocation,
        String coverLetter
) {
}
