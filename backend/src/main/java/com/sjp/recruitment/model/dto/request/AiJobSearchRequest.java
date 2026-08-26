package com.sjp.recruitment.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AiJobSearchRequest(
        @NotNull(message = "Vui lòng chọn CV để tìm việc bằng AI.") UUID cvId,
        boolean forceRefresh,
        @Valid AiJobSearchFilters filters
) {
    public AiJobSearchRequest {
        filters = filters == null ? AiJobSearchFilters.empty() : filters;
    }
}
