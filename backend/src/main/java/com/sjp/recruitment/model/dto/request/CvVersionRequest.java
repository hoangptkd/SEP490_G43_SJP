package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CvVersionRequest(
        @NotBlank String title,
        String templateKey,
        Map<String, Object> snapshot
) {
}
