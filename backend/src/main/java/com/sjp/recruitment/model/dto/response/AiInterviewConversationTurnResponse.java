package com.sjp.recruitment.model.dto.response;

public record AiInterviewConversationTurnResponse(
        String id,
        int sequence,
        String interviewerText,
        String rawTranscript,
        String finalTranscript,
        boolean transcriptEdited,
        int editCount,
        String answerStatus,
        boolean current,
        String answeredAt,
        String createdAt
) {
}
