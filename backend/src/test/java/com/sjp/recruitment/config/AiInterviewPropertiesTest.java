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
        assertEquals(8_000, properties.getTtsFirstAudioTimeoutMs());
        assertEquals(3_000, properties.getTtsIdleTimeoutMs());
    }

    @Test
    void supportsOnlyExplicitAnswerTranscriptionProviders() {
        AiInterviewProperties properties = new AiInterviewProperties();

        assertEquals("web_speech", properties.getAnswerTranscriptionProvider());
        assertTrue(properties.isAnswerTranscriptionConfigured());
        properties.setAnswerTranscriptionProvider("gladia_live");
        assertTrue(properties.isAnswerTranscriptionConfigured());
        properties.setAnswerTranscriptionProvider("unknown");
        assertFalse(properties.isAnswerTranscriptionConfigured());
    }

    @Test
    void usesConservativeTranscriptCorrectionDefaults() {
        AiInterviewProperties properties = new AiInterviewProperties();

        assertTrue(properties.isTranscriptCorrectionEnabled());
        assertEquals(0.90, properties.getTranscriptCorrectionMinConfidence());
        assertEquals(12, properties.getTranscriptCorrectionMaxCorrections());
        assertEquals(12, properties.getTranscriptCorrectionMaxEvidenceItems());
        assertEquals(100, properties.getTranscriptCorrectionMaxContextTerms());
        assertEquals(1_200, properties.getTranscriptCorrectionMaxPreviousContextChars());
        assertEquals(15_000, properties.getTranscriptCorrectionTimeoutMs());
        assertEquals("browser-transcript-correction-v1",
                properties.getTranscriptCorrectionPromptVersion());
    }
}
