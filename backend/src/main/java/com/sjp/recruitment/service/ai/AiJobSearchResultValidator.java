package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.sjp.recruitment.model.entity.Job;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiJobSearchResultValidator {

    public List<RankedJob> validate(
            JsonNode response,
            AiJobSearchContext context,
            List<AiJobSearchCandidateSelector.SelectedJob> candidates
    ) {
        JsonNode items = response == null ? null : response.get("items");
        if (items == null || !items.isArray()) {
            throw new AiJobSearchValidationException("Missing items array");
        }
        if (candidates.isEmpty()) {
            if (!items.isEmpty()) throw new AiJobSearchValidationException("Unexpected explanations for empty candidate list");
            return List.of();
        }
        if (items.size() != candidates.size()) {
            throw new AiJobSearchValidationException("Provider must explain every selected job");
        }

        Map<UUID, String> reasons = new HashMap<>();
        Set<UUID> expected = new HashSet<>();
        candidates.forEach(item -> expected.add(item.job().getId()));
        for (JsonNode item : items) {
            UUID jobId = parseUuid(item.path("jobId").asText(""));
            if (!expected.contains(jobId) || reasons.containsKey(jobId)) {
                throw new AiJobSearchValidationException("Unsupported or duplicate jobId");
            }
            reasons.put(jobId, boundedReason(item.path("reason").asText("")));
        }

        List<RankedJob> ranked = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            AiJobSearchCandidateSelector.SelectedJob selected = candidates.get(index);
            Job job = selected.job();
            String reason = reasons.get(job.getId());
            if (reason == null) throw new AiJobSearchValidationException("Missing explanation for selected job");
            ranked.add(new RankedJob(
                    index + 1,
                    job,
                    selected.score(),
                    reason
            ));
        }
        return List.copyOf(ranked);
    }

    private String boundedReason(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank() || normalized.length() > 500) {
            throw new AiJobSearchValidationException("Invalid provider reason");
        }
        return normalized;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception exception) {
            throw new AiJobSearchValidationException("Invalid jobId");
        }
    }

    public record RankedJob(
            int rank,
            Job job,
            AiJobMatchScorer.ScoreBreakdown scoreBreakdown,
            String reason
    ) {
        public int matchScore() { return scoreBreakdown.matchScore(); }
        public List<String> matchedSkills() { return scoreBreakdown.matchedSkills(); }
        public List<String> missingSkills() { return scoreBreakdown.missingSkills(); }
        public boolean lowConfidenceEvidence() { return scoreBreakdown.lowConfidenceEvidence(); }
    }
}
