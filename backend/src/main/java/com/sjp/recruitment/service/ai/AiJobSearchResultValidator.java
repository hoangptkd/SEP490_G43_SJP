package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.model.entity.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiJobSearchResultValidator {
    private final AiJobSearchProperties properties;

    public List<RankedJob> validate(
            JsonNode response,
            AiJobSearchContext context,
            List<AiJobSearchCandidateSelector.SelectedJob> candidates
    ) {
        JsonNode items = response == null ? null : response.path("items");
        if (items == null || !items.isArray()) {
            throw new AiJobSearchValidationException("Missing items array");
        }
        Map<UUID, Job> pool = new LinkedHashMap<>();
        candidates.stream().limit(properties.getMaxPromptJobs())
                .forEach(item -> pool.put(item.job().getId(), item.job()));
        Set<UUID> seen = new HashSet<>();
        List<RankedJob> ranked = new ArrayList<>();
        for (JsonNode item : items) {
            if (ranked.size() >= properties.getMaxResults()) break;
            UUID jobId = parseUuid(item.path("jobId").asText(""));
            Job job = pool.get(jobId);
            if (job == null || !seen.add(jobId)) {
                throw new AiJobSearchValidationException("Unsupported or duplicate jobId");
            }
            int matchScore = item.path("matchScore").asInt(-1);
            if (matchScore < 0 || matchScore > 100) {
                throw new AiJobSearchValidationException("matchScore outside 0-100");
            }
            String reason = boundedText(item.path("reason").asText(""), 500, true);
            List<String> matched = groundedMatchedSkills(item.path("matchedSkills"), context.skills(), job.getSkills());
            List<String> missing = groundedMissingSkills(item.path("missingSkills"), context.skills(), job.getSkills());
            ranked.add(new RankedJob(ranked.size() + 1, job, matchScore, matched, missing, reason));
        }
        if (!items.isEmpty() && ranked.isEmpty()) {
            throw new AiJobSearchValidationException("No valid ranked items");
        }
        return List.copyOf(ranked);
    }

    private List<String> groundedMatchedSkills(JsonNode node, List<String> candidateSkills, List<String> jobSkills) {
        Set<String> allowed = intersection(candidateSkills, jobSkills);
        return boundedSkills(node, allowed);
    }

    private List<String> groundedMissingSkills(JsonNode node, List<String> candidateSkills, List<String> jobSkills) {
        Set<String> candidate = normalizedSet(candidateSkills);
        Set<String> allowed = new LinkedHashSet<>();
        if (jobSkills != null) {
            jobSkills.stream().filter(Objects::nonNull).filter(skill -> !candidate.contains(normalize(skill)))
                    .forEach(skill -> allowed.add(normalize(skill)));
        }
        return boundedSkills(node, allowed);
    }

    private List<String> boundedSkills(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (result.size() >= 8) break;
            String value = boundedText(item.asText(""), 80, false);
            if (!value.isBlank() && allowed.contains(normalize(value))) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private Set<String> intersection(List<String> left, List<String> right) {
        Set<String> rightSet = normalizedSet(right);
        Set<String> result = new LinkedHashSet<>();
        if (left != null) {
            left.stream().filter(Objects::nonNull).map(this::normalize).filter(rightSet::contains).forEach(result::add);
        }
        return result;
    }

    private Set<String> normalizedSet(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) values.stream().filter(Objects::nonNull).map(this::normalize).forEach(result::add);
        return result;
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String boundedText(String value, int maxLength, boolean required) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if ((required && normalized.isBlank()) || normalized.length() > maxLength) {
            throw new AiJobSearchValidationException("Invalid provider text field");
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
            int matchScore,
            List<String> matchedSkills,
            List<String> missingSkills,
            String reason
    ) {
    }
}
