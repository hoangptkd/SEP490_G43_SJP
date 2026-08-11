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
    private String promptVersion = "ai-job-search-v1";
    private int providerConnectTimeoutMs = 5_000;
    private int providerReadTimeoutMs = 25_000;
    private int cacheHours = 24;
    private int maxCandidateJobs = 30;
    private int maxPromptJobs = 20;
    private int maxResults = 10;
    private int maxCvCharacters = 12_000;
    private int retentionDays = 90;

    public boolean isProviderConfigured() {
        return shopaikeyApiKey != null && !shopaikeyApiKey.isBlank();
    }
}
