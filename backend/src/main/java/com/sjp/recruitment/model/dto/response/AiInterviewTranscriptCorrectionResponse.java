package com.sjp.recruitment.model.dto.response;

import java.util.List;

public record AiInterviewTranscriptCorrectionResponse(
        String captureId,
        int captureVersion,
        String status,
        String proposedTranscript,
        int correctionCount,
        List<AiInterviewTranscriptCorrectionItemResponse> corrections,
        String candidateDecision
) {
    public AiInterviewTranscriptCorrectionResponse {
        corrections = corrections == null ? List.of() : List.copyOf(corrections);
    }
}
