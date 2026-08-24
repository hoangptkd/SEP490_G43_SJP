package com.sjp.recruitment.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiInterviewPropertiesTest {

    @Test
    void usesStableGeminiTtsTimeoutDefaults() {
        AiInterviewProperties properties = new AiInterviewProperties();

        assertEquals(2_000, properties.getTtsConnectTimeoutMs());
        assertEquals(12_000, properties.getTtsFirstAudioTimeoutMs());
        assertEquals(3_000, properties.getTtsIdleTimeoutMs());
        assertEquals(20_000, properties.getTtsPrefetchInitialWaitMs());
        assertEquals(1_200, properties.getTtsPrefetchPlaybackWaitMs());
        assertEquals(30_000, properties.getTtsPrefetchCooldownMs());
    }

    @Test
    void keepsTranscriptCorrectionDisabledByDefault() {
        AiInterviewProperties properties = new AiInterviewProperties();

        assertFalse(properties.isTranscriptCorrectionEnabled());
        assertEquals(0.90, properties.getTranscriptCorrectionMinConfidence());
        assertEquals(12, properties.getTranscriptCorrectionMaxCorrections());
        assertEquals(12, properties.getTranscriptCorrectionMaxEvidenceItems());
        assertEquals(100, properties.getTranscriptCorrectionMaxContextTerms());
        assertEquals(1_200, properties.getTranscriptCorrectionMaxPreviousContextChars());
        assertEquals(15_000, properties.getTranscriptCorrectionTimeoutMs());
        assertEquals("browser-transcript-correction-v2",
                properties.getTranscriptCorrectionPromptVersion());
    }

    @Test
    void usesSpeechmaticsRealtimeAsTheDefaultAnswerProvider() {
        AiInterviewProperties properties = new AiInterviewProperties();

        assertEquals("speechmatics_realtime", properties.getAnswerTranscriptionProvider());
        assertTrue(properties.isSpeechmaticsRealtimeEnabled());
        assertEquals("vi", properties.getSpeechmaticsLanguage());
        assertEquals("standard", properties.getSpeechmaticsOperatingPoint());
        assertEquals(15_000, properties.getSpeechmaticsFinalFlushTimeoutMs());
        assertFalse(properties.isAnswerTranscriptionConfigured());

        properties.setSpeechmaticsApiKey("test-key");
        assertTrue(properties.isAnswerTranscriptionConfigured());
    }

    @Test
    void usesApprovedGeminiRoutesByDefault() {
        AiInterviewProperties properties = new AiInterviewProperties();

        assertEquals("gemini-2.5-pro", properties.getTextAi().getCvAnalysis().getModel());
        assertFalse(properties.getTextAi().getCvAnalysis().isReasoningEnabled());
        assertEquals(3, properties.getTextAi().getCvAnalysis().getMaxAttempts());

        assertEquals("gemini-3.5-flash", properties.getTextAi().getInitialQuestions().getModel());
        assertFalse(properties.getTextAi().getInitialQuestions().isReasoningEnabled());
        assertEquals(3, properties.getTextAi().getInitialQuestions().getMaxAttempts());

        assertEquals("gemini-3.5-flash-lite", properties.getTextAi().getTurnDecision().getModel());
        assertFalse(properties.getTextAi().getTurnDecision().isReasoningEnabled());
        assertEquals(1, properties.getTextAi().getTurnDecision().getMaxAttempts());
        assertEquals(15_000, properties.getTextAi().getTurnDecision().getReadTimeoutMs());

        assertEquals("gemini-3.1-flash-lite", properties.getTextAi().getTurnEvidence().getModel());
        assertFalse(properties.getTextAi().getTurnEvidence().isReasoningEnabled());
        assertEquals(3, properties.getTextAi().getTurnEvidence().getMaxAttempts());

        assertEquals("gemini-3.5-flash", properties.getTextAi().getAdaptiveQuestions().getModel());
        assertFalse(properties.getTextAi().getAdaptiveQuestions().isReasoningEnabled());
        assertEquals(3, properties.getTextAi().getAdaptiveQuestions().getMaxAttempts());

        assertEquals("gemini-2.5-flash-lite",
                properties.getTextAi().getTranscriptCorrection().getModel());
        assertFalse(properties.getTextAi().getTranscriptCorrection().isReasoningEnabled());
        assertEquals(3, properties.getTextAi().getTranscriptCorrection().getMaxAttempts());

        assertEquals("gemini-2.5-pro", properties.getTextAi().getFinalEvaluation().getModel());
        assertFalse(properties.getTextAi().getFinalEvaluation().isReasoningEnabled());
        assertEquals(3, properties.getTextAi().getFinalEvaluation().getMaxAttempts());
    }
}
