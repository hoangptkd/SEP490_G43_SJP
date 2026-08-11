package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record AiJobSearchResponse(
        String source,
        boolean cached,
        boolean stale,
        boolean lowConfidence,
        LocalDateTime generatedAt,
        LocalDateTime expiresAt,
        AiJobSearchQuotaResponse quota,
        List<AiJobSearchItemResponse> items
) {
}
