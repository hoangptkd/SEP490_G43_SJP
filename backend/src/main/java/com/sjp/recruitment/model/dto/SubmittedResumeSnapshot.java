package com.sjp.recruitment.model.dto;

import com.sjp.recruitment.model.entity.CandidateCv;
import com.sjp.recruitment.model.entity.CvVersion;

import java.time.LocalDateTime;
import java.util.Map;

public record SubmittedResumeSnapshot(
        String sourceType,
        String resumeId,
        String title,
        String originalFileName,
        String contentType,
        Long fileSize,
        String templateKey,
        Map<String, Object> builderSnapshot,
        LocalDateTime sourceUpdatedAt
) {
    public static SubmittedResumeSnapshot fromUploaded(CandidateCv cv) {
        return new SubmittedResumeSnapshot(
                "uploaded",
                String.valueOf(cv.getId()),
                cv.getTitle(),
                cv.getOriginalFileName(),
                cv.getContentType(),
                cv.getFileSize(),
                null,
                null,
                cv.getUpdatedAt() != null ? cv.getUpdatedAt() : cv.getCreatedAt()
        );
    }

    public static SubmittedResumeSnapshot fromBuilder(CvVersion version) {
        return new SubmittedResumeSnapshot(
                "builder",
                String.valueOf(version.getId()),
                version.getTitle(),
                null,
                null,
                null,
                version.getTemplateKey(),
                version.getSnapshot(),
                version.getUpdatedAt() != null ? version.getUpdatedAt() : version.getCreatedAt()
        );
    }
}
