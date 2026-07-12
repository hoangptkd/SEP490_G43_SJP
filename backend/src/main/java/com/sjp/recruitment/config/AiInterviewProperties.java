package com.sjp.recruitment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai-interview")
@Data
public class AiInterviewProperties {
    private String gladiaApiKey;
    private String gladiaBaseUrl = "https://api.gladia.io";
    private String shopaikeyApiKey;
    private String shopaikeyBaseUrl = "https://api.shopaikey.com/v1";
    private String shopaikeyModel = "gpt-5-mini-2025-08-07";
    private int audioMaxSeconds = 180;
    private int audioMaxSizeMb = 25;
    private int questionCount = 5;
    private int providerConnectTimeoutMs = 5_000;
    private int providerReadTimeoutMs = 20_000;
    private boolean voiceStreamingEnabled = true;
    private String voiceProvider = "browser_web_speech";
    private int voiceSilenceMs = 4_000;
    private int costlyRequestsPerMinute = 12;

    public boolean isEnabled() {
        return hasText(gladiaApiKey) && hasText(shopaikeyApiKey);
    }

    public long audioMaxBytes() {
        return audioMaxSizeMb * 1024L * 1024L;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
