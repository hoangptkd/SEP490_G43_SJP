package com.sjp.recruitment.model.dto.response;

public record AiInterviewLiveTranscriptionResponse(
        String provider,
        String sessionToken,
        String jobId,
        String websocketUrl,
        String targetType,
        String targetId
) {
}
