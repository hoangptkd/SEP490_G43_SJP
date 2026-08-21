package com.sjp.recruitment.model.dto.response;

public record AiInterviewConfigResponse(
        boolean enabled,
        String message,
        int questionCount,
        int audioMaxSeconds,
        int audioMaxSizeMb,
        String answerTranscriptionProvider,
        boolean speechmaticsRealtimeEnabled,
        boolean voiceStreamingEnabled,
        String voiceProvider,
        int voiceConfirmationPromptDelayMs,
        int voiceConfirmationAutoFinalizeMs,
        int voiceRecognitionRestartDelayMs,
        int voiceLoadWaitMs,
        int voiceNextQuestionDelayMs
) {
}
