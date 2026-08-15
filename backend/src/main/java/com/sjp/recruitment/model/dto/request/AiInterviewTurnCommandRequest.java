package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.Positive;

public record AiInterviewTurnCommandRequest(
        @Positive Integer expectedDialogueVersion
) {
}
