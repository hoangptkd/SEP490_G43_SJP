package com.sjp.recruitment.service.ai;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Shared by prompt guidance and validation. Never infer capabilities from related technologies. */
final class AiJobSearchSkillEvidence {
    private static final String LEFT_BOUNDARY = "(?<![\\p{L}\\p{N}+#])";
    private static final String RIGHT_BOUNDARY = "(?![\\p{L}\\p{N}+#])";
    // Explicit spelling equivalents only; e.g. MySQL must not become evidence for SQL.
    private static final Pattern REST_API = Pattern.compile(LEFT_BOUNDARY + "rest(?:ful)? apis?" + RIGHT_BOUNDARY);

    private AiJobSearchSkillEvidence() {}

    static String key(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Z}\\s]+", " ").trim();
        return REST_API.matcher(normalized).replaceAll("rest api");
    }

    static boolean contains(String text, String skill) {
        String normalizedSkill = key(skill);
        return !normalizedSkill.isBlank() && Pattern.compile(LEFT_BOUNDARY + Pattern.quote(normalizedSkill)
                + RIGHT_BOUNDARY).matcher(key(text)).find();
    }

    static Options options(String cvText, Collection<String> jobSkills) {
        Map<String, String> unique = new LinkedHashMap<>();
        if (jobSkills != null) {
            jobSkills.stream().filter(Objects::nonNull).map(String::trim)
                    .filter(skill -> !skill.isBlank() && skill.length() <= 100)
                    .forEach(skill -> unique.putIfAbsent(key(skill), skill));
        }
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        unique.values().forEach(skill -> (contains(cvText, skill) ? matched : missing).add(skill));
        return new Options(List.copyOf(matched), List.copyOf(missing));
    }

    record Options(List<String> matched, List<String> missing) {}
}
