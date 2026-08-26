package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AiJobSearchResponse(
        String source,
        UUID cvId,
        UUID runId,
        boolean cached,
        boolean stale,
        boolean lowConfidence,
        LocalDateTime generatedAt,
        LocalDateTime expiresAt,
        AiJobSearchQuotaResponse quota,
        List<AiJobSearchItemResponse> items
) {
}
