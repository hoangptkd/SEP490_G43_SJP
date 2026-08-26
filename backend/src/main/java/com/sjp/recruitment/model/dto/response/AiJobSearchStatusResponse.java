package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record AiJobSearchStatusResponse(
        boolean enabled,
        boolean consentRequired,
        String policyVersion,
        Readiness readiness,
        AiJobSearchQuotaResponse quota,
        Cache cache
) {
    public record Readiness(
            boolean profileAvailable,
            boolean cvAvailable,
            boolean lowConfidence,
            List<String> missingItems
    ) {
    }

    public record Cache(
            boolean available,
            LocalDateTime generatedAt,
            LocalDateTime expiresAt,
            boolean stale
    ) {
    }
}
