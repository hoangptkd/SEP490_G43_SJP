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

    public ScoreBreakdown score(AiJobSearchContext context, Job job) {
        List<String> jobSkills = normalizedDisplaySkills(job.getSkills());
        Set<String> candidateSkills = normalizedPhrases(context.skills());

        List<String> matchedSkills = jobSkills.stream()
                .filter(skill -> candidateSkills.contains(normalizePhrase(skill)))
                .toList();
        List<String> missingSkills = jobSkills.stream()
                .filter(skill -> !candidateSkills.contains(normalizePhrase(skill)))
                .toList();

        double skillScore = jobSkills.isEmpty()
                ? 20.0
                : 40.0 * matchedSkills.size() / jobSkills.size();
        double experienceScore = experienceScore(context.experienceLevel(), job.getExperienceLevel());
        double targetRoleScore = targetRoleScore(context, job);
        double cvJdScore = cvJdScore(context, job);
        double locationScore = locationScore(context, job.getLocation(), job.getWorkMode());
        int total = (int) Math.round(clamp(skillScore, 0, 40)
                + clamp(experienceScore, 0, 20)
                + clamp(targetRoleScore, 0, 15)
                + clamp(cvJdScore, 0, 15)
                + clamp(locationScore, 0, 10));

        return new ScoreBreakdown(
                Math.max(0, Math.min(100, total)),
                skillScore,
                experienceScore,
                targetRoleScore,
                cvJdScore,
                locationScore,
                matchedSkills,
                missingSkills,
                jobSkills.isEmpty()
        );
    }

    private double experienceScore(String candidateLevel, String jobLevel) {
        Integer candidate = experienceLevel(candidateLevel);
        Integer required = experienceLevel(jobLevel);
        if (candidate == null || required == null) return 10.0;
        if (candidate >= required) return 20.0;
        return required - candidate == 1 ? 12.0 : 4.0;
    }

    private Integer experienceLevel(String value) {
        String normalized = normalizePhrase(value).toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return null;
        if (normalized.contains("INTERN") || normalized.contains("THUC TAP")) return 0;
        if (normalized.contains("FRESH") || normalized.contains("ENTRY") || normalized.contains("MOI RA TRUONG")) return 1;
        if (normalized.contains("JUNIOR")) return 2;
        if (normalized.contains("MIDDLE") || normalized.contains("MID-LEVEL") || normalized.contains("INTERMEDIATE")) return 3;
        if (normalized.contains("SENIOR")) return 4;
        if (normalized.contains("LEAD") || normalized.contains("MANAGER") || normalized.contains("TRUONG")) return 5;
        return null;
    }

    private double targetRoleScore(AiJobSearchContext context, Job job) {
        Set<String> titleTokens = tokens(job.getTitle());
        if (titleTokens.isEmpty()) return 7.5;
        Set<String> candidateVocabulary = new LinkedHashSet<>(tokens(context.headline()));
        if (context.desiredJobTitles() != null) {
            context.desiredJobTitles().forEach(title -> candidateVocabulary.addAll(tokens(title)));
        }
        if (context.skills() != null) context.skills().forEach(skill -> candidateVocabulary.addAll(tokens(skill)));
        long matched = titleTokens.stream().filter(candidateVocabulary::contains).count();
        return 15.0 * matched / titleTokens.size();
    }

    private double cvJdScore(AiJobSearchContext context, Job job) {
        Set<String> jdKeywords = tokens(join(job.getDescription(), job.getRequirementsText()));
        if (jdKeywords.isEmpty()) return 7.5;
        Set<String> candidateVocabulary = tokens(candidateText(context));
        long matched = jdKeywords.stream().filter(candidateVocabulary::contains).count();
        return 15.0 * matched / jdKeywords.size();
    }

    private double locationScore(AiJobSearchContext context, String jobLocation, String workMode) {
        String normalizedMode = normalizePhrase(workMode);
        if (normalizedMode.equals("remote") || normalizedMode.contains("tu xa")) return 10.0;
        List<String> preferredLocations = context.preferredLocations() == null
                ? List.of()
                : context.preferredLocations();
        if (!preferredLocations.isEmpty()) {
            String job = normalizePhrase(jobLocation);
            if (job.isBlank()) return 5.0;
            boolean matches = preferredLocations.stream()
                    .map(this::normalizePhrase)
                    .anyMatch(location -> !location.isBlank() && (location.contains(job) || job.contains(location)));
            return matches ? 10.0 : context.willingToRelocate() ? 5.0 : 0.0;
        }
        String candidate = normalizePhrase(context.location());
        String job = normalizePhrase(jobLocation);
        if (candidate.isBlank() || job.isBlank()) return 5.0;
        return candidate.contains(job) || job.contains(candidate) ? 10.0 : 0.0;
    }

    private String candidateText(AiJobSearchContext context) {
        StringBuilder value = new StringBuilder();
        append(value, context.headline());
        append(value, context.bio());
        append(value, context.cvText());
        append(value, context.skills());
        append(value, context.education());
        append(value, context.workExperience());
        append(value, context.projects());
        append(value, context.certifications());
        return value.toString();
    }

    private void append(StringBuilder target, Object value) {
        if (value != null) target.append(' ').append(value);
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

    private Set<String> normalizedPhrases(Collection<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) values.stream().filter(Objects::nonNull).map(this::normalizePhrase)
                .filter(value -> !value.isBlank()).forEach(result::add);
        return result;
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

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record ScoreBreakdown(
            int matchScore,
            double skillScore,
            double experienceScore,
            double targetRoleScore,
            double cvJdScore,
            double locationScore,
            List<String> matchedSkills,
            List<String> missingSkills,
            boolean lowConfidenceEvidence
    ) {
    }
}
