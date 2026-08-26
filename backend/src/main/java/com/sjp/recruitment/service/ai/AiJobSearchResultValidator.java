package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.model.dto.response.AiJobSearchItemResponse.Evidence;
import com.sjp.recruitment.model.entity.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiJobSearchResultValidator {
    private final AiJobSearchProperties properties;

    public List<RankedJob> validate(JsonNode response, AiJobSearchContext context,
                                   List<AiJobSearchCandidateSelector.SelectedJob> candidates) {
        JsonNode items = response == null ? null : response.get("items");
        int expectedCount = Math.min(candidates.size(), Math.max(1, Math.min(properties.getMaxResults(), 10)));
        if (items == null || !items.isArray() || items.size() != expectedCount) {
            throw invalid("ITEM_COUNT", "items", "Return exactly " + expectedCount + " ranked jobs in the items array.");
        }
        Map<UUID, Job> allowed = new HashMap<>();
        candidates.forEach(item -> allowed.put(item.job().getId(), item.job()));
        Set<UUID> seen = new HashSet<>();
        List<RankedJob> ranked = new ArrayList<>();
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            JsonNode item = items.get(itemIndex);
            String path = "items[" + itemIndex + "]";
            UUID id;
            try {
                id = UUID.fromString(item.path("jobId").asText(""));
            } catch (IllegalArgumentException exception) {
                throw invalid("JOB_ID", path + ".jobId", "Use a valid jobId UUID from CandidateJobs.");
            }
            Job job = allowed.get(id);
            if (job == null || !seen.add(id)) {
                throw invalid("JOB_ID", path + ".jobId", "Use only CandidateJobs IDs, each at most once.");
            }
            JsonNode score = item.path("matchScore");
            if (!score.isIntegralNumber() || !score.canConvertToInt() || score.intValue() < 0 || score.intValue() > 100) {
                throw invalid("SCORE", path + ".matchScore", "Use a JSON integer from 0 to 100, not a string or decimal.");
            }
            String jobText = String.join(" ", safe(job.getTitle()), safe(job.getDescription()),
                    safe(job.getRequirementsText()), String.join(" ", job.getSkills()));
            List<String> matched = skills(item.get("matchedSkills"), jobText, path + ".matchedSkills");
            List<String> missing = skills(item.get("missingSkills"), jobText, path + ".missingSkills");
            for (int skillIndex = 0; skillIndex < matched.size(); skillIndex++) {
                if (!AiJobSearchSkillEvidence.contains(context.cvText(), matched.get(skillIndex))) {
                    throw invalid("MATCHED_SKILL_NOT_IN_CV", path + ".matchedSkills[" + skillIndex + "]",
                            "Remove this entry: it is not evidenced in cvContent. Rebuild ALL matchedSkills from each job's matchedSkillOptions, or use []. Do not infer SQL from MySQL, OOP from Java, or other related capabilities; adjust reason and score if they relied on unsupported skills.");
                }
            }
            Set<String> matchedKeys = new HashSet<>();
            matched.forEach(skill -> matchedKeys.add(AiJobSearchSkillEvidence.key(skill)));
            for (int skillIndex = 0; skillIndex < missing.size(); skillIndex++) {
                String skill = missing.get(skillIndex);
                if (matchedKeys.contains(AiJobSearchSkillEvidence.key(skill)) || AiJobSearchSkillEvidence.contains(context.cvText(), skill)) {
                    throw invalid("INCONSISTENT_SKILL_GAP", path + ".missingSkills[" + skillIndex + "]",
                            "Remove this entry: it is already evidenced in cvContent or matchedSkills. Rebuild ALL missingSkills from each job's missingSkillOptions, or use [].");
                }
            }
            JsonNode evidenceNodes = item.get("evidence");
            if (evidenceNodes == null || !evidenceNodes.isArray() || evidenceNodes.isEmpty() || evidenceNodes.size() > 3) {
                throw invalid("EVIDENCE_COUNT", path + ".evidence", "Provide 1 to 3 evidence pairs; prefer one short pair.");
            }
            List<Evidence> evidence = new ArrayList<>();
            for (int evidenceIndex = 0; evidenceIndex < evidenceNodes.size(); evidenceIndex++) {
                JsonNode node = evidenceNodes.get(evidenceIndex);
                String evidencePath = path + ".evidence[" + evidenceIndex + "]";
                String cvQuote = boundedText(node.get("cvQuote"), 5, 240, evidencePath + ".cvQuote");
                String jobQuote = boundedText(node.get("jobQuote"), 5, 240, evidencePath + ".jobQuote");
                if (!whitespace(context.cvText()).contains(cvQuote)) {
                    throw invalid("CV_QUOTE_NOT_FOUND", evidencePath + ".cvQuote", "Return cvQuoteIndex for one relevant entry in CvQuoteOptions instead of writing cvQuote text. Do not paraphrase or join passages. Check ALL evidence references again.");
                }
                if (!whitespace(jobText).contains(jobQuote)) {
                    throw invalid("JOB_QUOTE_NOT_FOUND", evidencePath + ".jobQuote", "Return jobQuoteIndex for one relevant entry in this job's jobQuoteOptions instead of writing jobQuote text. Do not paraphrase or join passages. Check ALL evidence references again.");
                }
                evidence.add(new Evidence(cvQuote, jobQuote));
            }
            ranked.add(new RankedJob(0, job, score.intValue(), matched, missing,
                    boundedText(item.get("reason"), 1, 500, path + ".reason"), List.copyOf(evidence)));
        }
        // Sort by AI scores, never by the preliminary keyword score. Ties retain provider order.
        ranked.sort(Comparator.comparingInt(RankedJob::matchScore).reversed());
        List<RankedJob> result = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            RankedJob item = ranked.get(index);
            result.add(new RankedJob(index + 1, item.job(), item.matchScore(), item.matchedSkills(),
                    item.missingSkills(), item.reason(), item.evidence()));
        }
        return List.copyOf(result);
    }

    private List<String> skills(JsonNode values, String jobText, String path) {
        if (values == null || !values.isArray() || values.size() > 20) {
            throw invalid("SKILLS_ARRAY", path, "Use an array of at most 20 skill names; use [] if none are evidenced.");
        }
        Set<String> keys = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            String skillPath = path + "[" + index + "]";
            String skill = boundedText(values.get(index), 1, 100, skillPath);
            if (!keys.add(AiJobSearchSkillEvidence.key(skill)) || !AiJobSearchSkillEvidence.contains(jobText, skill)) {
                throw invalid("UNSUPPORTED_SKILL", skillPath, "Use unique skill names appearing literally in this job's skills or JD.");
            }
            result.add(skill);
        }
        return List.copyOf(result);
    }

    private String boundedText(JsonNode node, int min, int max, String path) {
        if (node == null || !node.isTextual()) throw invalid("TEXT_TYPE", path, "Use a JSON string.");
        String text = whitespace(node.textValue());
        if (text.length() < min || text.length() > max) {
            String hint = path.endsWith(".cvQuote") ? " Return cvQuoteIndex from CvQuoteOptions instead of writing quote text."
                    : path.endsWith(".jobQuote") ? " Return jobQuoteIndex from this job's jobQuoteOptions instead of writing quote text." : "";
            throw invalid("TEXT_LENGTH", path, "Use text between " + min + " and " + max + " characters." + hint);
        }
        return text;
    }

    private String whitespace(String value) { return safe(value).replaceAll("[\\p{Z}\\s]+", " ").trim(); }
    private String safe(String value) { return value == null ? "" : value; }
    private AiJobSearchValidationException invalid(String code, String path, String message) {
        return new AiJobSearchValidationException(code, path, message);
    }

    public record RankedJob(int rank, Job job, int matchScore, List<String> matchedSkills,
                            List<String> missingSkills, String reason, List<Evidence> evidence) {}
}
