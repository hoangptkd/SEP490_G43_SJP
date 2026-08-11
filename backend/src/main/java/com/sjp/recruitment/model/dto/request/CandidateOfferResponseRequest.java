package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CandidateOfferResponseRequest(
        @NotNull Decision decision,
        @Size(max = 1000) String note
) {
    public enum Decision {
        ACCEPT,
        REJECT
    }

    public boolean accepted() {
        return decision == Decision.ACCEPT;
    }
}
