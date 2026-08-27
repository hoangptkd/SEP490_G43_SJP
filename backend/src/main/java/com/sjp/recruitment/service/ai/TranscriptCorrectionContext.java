package com.sjp.recruitment.service.ai;

import java.util.List;

public record TranscriptCorrectionContext(
        String currentQuestion,
        String rawTranscript,
        List<String> cvTechnicalTerms,
        List<String> jobTechnicalTerms,
        List<String> relevantTechnicalVocabulary,
        String previousContext
) {
}
