package com.sjp.recruitment.model.dto.response;

public record FeatureUsageResponse(
        String featureKey,
        String label,
        int used,
        int limit,
        boolean daily
) {
}
