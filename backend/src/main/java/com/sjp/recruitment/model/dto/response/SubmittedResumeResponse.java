package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

public record SubmittedResumeResponse(
        String sourceType,
        String resumeId,
        String title,
        String originalFileName,
        String contentType,
        Long fileSize,
        String templateKey,
        Map<String, Object> builderSnapshot,
        LocalDateTime sourceUpdatedAt,
        boolean downloadAvailable
) {
}
