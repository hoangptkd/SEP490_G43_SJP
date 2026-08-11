package com.sjp.recruitment.model.dto.response;

import java.time.OffsetDateTime;

public record AiJobSearchQuotaResponse(
        int used,
        int limit,
        int remaining,
        OffsetDateTime resetAt
) {
}
