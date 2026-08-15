package com.sjp.recruitment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.ai-interview")
@Data
public class AiInterviewProperties {
    private String gladiaApiKey;
    private String gladiaBaseUrl = "https://api.gladia.io";
    private String shopaikeyApiKey;
    private String shopaikeyBaseUrl = "https://api.shopaikey.com/v1";
    private String shopaikeyModel = "gpt-4.1-mini";
    private String ttsBaseUrl = "https://api.shopaikey.com";
    private String ttsModel = "gemini-3.1-flash-tts-preview";
    private String ttsVoice = "Kore";
    private String ttsVoiceInstruction = "Speak naturally as a professional Vietnamese interviewer. "
            + "Use a warm, conversational tone and moderate pace. "
            + "Pronounce English technical terms naturally in English. "
            + "Do not add or remove words.";
    private int ttsConnectTimeoutMs = 2_000;
    private int ttsFirstAudioTimeoutMs = 8_000;
    private int ttsIdleTimeoutMs = 3_000;
    private int ttsCacheMaxEntries = 100;
    private int ttsCacheTtlSeconds = 1_800;
    private int audioMaxSeconds = 180;
    private int audioMaxSizeMb = 25;
    private int questionCount = 5;
    private int maxProbesPerCore = 1;
    private int maxClarifiesPerCore = 1;
    private int maxTotalAssessmentTurns = 10;
    private int providerConnectTimeoutMs = 5_000;
    private int providerReadTimeoutMs = 60_000;
    private boolean voiceStreamingEnabled = true;
    private String voiceProvider = "shopaikey_gemini_stream";
    private String answerTranscriptionProvider = "web_speech";
    private int voiceConfirmationPromptDelayMs = 2_000;
    private int voiceConfirmationAutoFinalizeMs = 3_000;
    private int voiceRecognitionRestartDelayMs = 250;
    private int voiceLoadWaitMs = 700;
    private int voiceNextQuestionDelayMs = 150;
    private int voiceEvidenceCorePoolSize = 2;
    private int voiceEvidenceQueueCapacity = 4;
    private int voiceEvidenceAwaitTimeoutMs = 5_000;
    private boolean transcriptCorrectionEnabled = true;
    private double transcriptCorrectionMinConfidence = 0.90;
    private int transcriptCorrectionMaxCorrections = 12;
    private int transcriptCorrectionMaxEvidenceItems = 12;
    private int transcriptCorrectionMaxContextTerms = 100;
    private int transcriptCorrectionMaxPreviousContextChars = 1_200;
    private int transcriptCorrectionTimeoutMs = 15_000;
    private String transcriptCorrectionPromptVersion = "browser-transcript-correction-v1";
    private int costlyRequestsPerMinute = 12;
    private String cvProfilePromptVersion = "cv-interview-profile-v1";
    private String speechCalibrationVersion = "speech-reference-v1";
    // Configurable reference bands. They are coaching defaults, not validated hiring thresholds.
    private double speechRateL0 = 90;
    private double speechRateL1 = 150;
    private double speechRateU1 = 300;
    private double speechRateU0 = 420;
    private double articulationRateL0 = 120;
    private double articulationRateL1 = 180;
    private double articulationRateU1 = 360;
    private double articulationRateU0 = 480;
    private double pauseRatioL0 = 0.0;
    private double pauseRatioL1 = 0.05;
    private double pauseRatioU1 = 0.35;
    private double pauseRatioU0 = 0.60;
    private double pauseFrequencyL0 = 0.0;
    private double pauseFrequencyL1 = 1.0;
    private double pauseFrequencyU1 = 12.0;
    private double pauseFrequencyU0 = 20.0;
    private double longestPauseL0 = 0.0;
    private double longestPauseL1 = 0.15;
    private double longestPauseU1 = 2.5;
    private double longestPauseU0 = 6.0;
    private List<String> gladiaLanguages = new ArrayList<>(List.of("vi", "en"));
    private boolean gladiaCodeSwitchingEnabled = true;
    private boolean gladiaCustomVocabularyEnabled = true;
    private double gladiaCustomVocabularyIntensity = 0.5;
    private int gladiaCustomVocabularyMaxItems = 100;
    private List<String> gladiaBaseVocabulary = new ArrayList<>(List.of(
            "Spring Boot", "Java", "REST API", "Hibernate", "JPA", "PostgreSQL",
            "Docker", "Kubernetes", "Redis", "Kafka", "JWT", "OAuth", "React", "TypeScript"
    ));

    public boolean isEnabled() {
        return isCoreConfigured() && isVoiceConfigured();
    }

    public boolean isCoreConfigured() {
        return hasText(gladiaApiKey) && hasText(shopaikeyApiKey);
    }

    public boolean isVoiceConfigured() {
        if (!isAnswerTranscriptionConfigured()) {
            return false;
        }
        if (!voiceStreamingEnabled) {
            return true;
        }
        if ("shopaikey_gemini_stream".equalsIgnoreCase(voiceProvider)) {
            return hasText(shopaikeyApiKey)
                    && hasText(ttsBaseUrl)
                    && hasText(ttsModel)
                    && hasText(ttsVoice)
                    && hasText(ttsVoiceInstruction)
                    && ttsConnectTimeoutMs > 0
                    && ttsFirstAudioTimeoutMs > 0
                    && ttsIdleTimeoutMs > 0
                    && ttsCacheMaxEntries > 0
                    && ttsCacheTtlSeconds > 0;
        }
        return false;
    }

    public boolean isAnswerTranscriptionConfigured() {
        return "web_speech".equalsIgnoreCase(answerTranscriptionProvider)
                || "gladia_live".equalsIgnoreCase(answerTranscriptionProvider);
    }

    public long audioMaxBytes() {
        return audioMaxSizeMb * 1024L * 1024L;
    }

    public int effectiveCoreQuestionCount() {
        return 5;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
