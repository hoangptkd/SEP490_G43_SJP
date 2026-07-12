package com.sjp.recruitment.model.dto.response;

public record AiInterviewConfigResponse(
        boolean enabled,
        String message,
        int questionCount,
        int audioMaxSeconds,
        int audioMaxSizeMb,
        boolean voiceStreamingEnabled,
        String voiceProvider,
        int voiceSilenceMs
) {
}
