package com.sjp.recruitment.model.dto.response;

import java.util.List;

public record AiInterviewCvProfileResponse(
        String id,
        String cvId,
        String cvTitle,
        String contentHash,
        String summary,
        String experienceLevel,
        List<String> skills,
        List<RoleSuggestion> suggestedRoles,
        List<EvidenceClaim> evidenceClaims,
        String promptVersion,
        boolean cached
) {
    public record RoleSuggestion(String title, String reason) {
    }

    public record EvidenceClaim(String id, String topic, String claim) {
    }
}
