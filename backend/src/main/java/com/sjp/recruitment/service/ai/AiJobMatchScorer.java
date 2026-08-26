package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.model.entity.Job;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class AiJobMatchScorer {
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}+#.]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Set<String> STOP_WORDS = Set.of(
            "anh", "ban", "cac", "cho", "co", "cua", "duoc", "hay", "khi", "la", "mot", "nhung",
            "theo", "trong", "tu", "va", "ve", "voi", "yeu", "can", "cong", "viec", "ung", "vien",
            "and", "are", "for", "from", "into", "job", "the", "this", "that", "with", "you", "your"
    );

    /** Retrieval only: never exposed as an AI match percentage. No Profile capabilities are used. */
    public int shortlistScore(AiJobSearchContext context, Job job) {
        String cv = normalizePhrase(context.cvText());
        Set<String> vocabulary = tokens(context.cvText());
        List<String> skills = normalizedDisplaySkills(job.getSkills());
        long matchedSkills = skills.stream().filter(skill -> containsPhrase(cv, normalizePhrase(skill))).count();
        Set<String> title = tokens(job.getTitle());
        Set<String> jd = tokens(join(job.getDescription(), job.getRequirementsText()));
        double skillPart = skills.isEmpty() ? 0 : 60.0 * matchedSkills / skills.size();
        double titlePart = title.isEmpty() ? 0 : 25.0 * title.stream().filter(vocabulary::contains).count() / title.size();
        double jdPart = jd.isEmpty() ? 0 : 15.0 * jd.stream().filter(vocabulary::contains).count() / jd.size();
        return (int) Math.round(skillPart + titlePart + jdPart);
    }

    private boolean containsPhrase(String text, String phrase) {
        return !phrase.isBlank() && Pattern.compile("(?<![\\p{L}\\p{N}+#])" + Pattern.quote(phrase)
                + "(?![\\p{L}\\p{N}+#])").matcher(text).find();
    }

    private String join(String... values) {
        return String.join(" ", Arrays.stream(values).map(value -> value == null ? "" : value).toList());
    }

    private List<String> normalizedDisplaySkills(Collection<String> values) {
        if (values == null) return List.of();
        Map<String, String> unique = new TreeMap<>();
        values.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank())
                .forEach(value -> unique.putIfAbsent(normalizePhrase(value), value));
        return List.copyOf(unique.values());
    }

    private Set<String> tokens(String value) {
        String normalized = normalizePhrase(value);
        if (normalized.isBlank()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(TOKEN_SEPARATOR.split(normalized))
                .map(String::trim)
                .filter(this::usableToken)
                .filter(token -> !STOP_WORDS.contains(token))
                .forEach(result::add);
        return result;
    }

    private boolean usableToken(String token) {
        return token.length() >= 3 || token.contains("#") || token.contains("+");
    }

    private String normalizePhrase(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd');
        normalized = COMBINING_MARKS.matcher(normalized).replaceAll("");
        return WHITESPACE.matcher(normalized).replaceAll(" ").trim();
    }

}
