package com.sjp.recruitment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai-job-search")
@Data
public class AiJobSearchProperties {
    private boolean enabled = true;
    private String shopaikeyApiKey;
    private String shopaikeyBaseUrl = "https://api.shopaikey.com/v1";
    private String shopaikeyModel = "gpt-4.1-mini";
    private String promptVersion = "selected-cv-ranking-v4";
    private String scoringVersion = "cv-shortlist-v2";
    private int providerConnectTimeoutMs = 5_000;
    private int providerReadTimeoutMs = 60_000;
    private int cacheHours = 24;
    private int maxCandidateJobs = 30;
    private int maxPromptJobs = 30;
    private int maxResults = 10;
    private int maxCvCharacters = 12_000;
    private int retentionDays = 90;

    public boolean isProviderConfigured() {
        return shopaikeyApiKey != null && !shopaikeyApiKey.isBlank();
    }
}
