package com.sjp.recruitment.model.dto.response;

import java.util.List;

public record AiInterviewConversationResponse(
        String dialogueState,
        int version,
        String currentTurnId,
        boolean expectsAnswer,
        String speechText,
        int completedCoreQuestions,
        int totalCoreQuestions,
        String errorStage,
        String errorCode,
        String errorMessage,
        List<AiInterviewConversationTurnResponse> timeline
) {
    public AiInterviewConversationResponse {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
    }
}
