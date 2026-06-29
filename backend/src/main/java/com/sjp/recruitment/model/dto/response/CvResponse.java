package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;

public record CvResponse(
        String id,
        String originalFileName,
        String contentType,
        Long fileSize,
        boolean defaultCv,
        boolean deleted,
        LocalDateTime createdAt
) {
}
