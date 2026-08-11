package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiJobSearchCandidateSelector {
    private final JobRepository jobRepository;
    private final AiJobSearchProperties properties;

    @Transactional(readOnly = true)
    public List<SelectedJob> select(AiJobSearchContext context) {
        Set<String> candidateSkills = normalize(context.skills());
        List<SelectedJob> selected = jobRepository.findPublicJobsForAi(LocalDate.now()).stream()
                .map(job -> new SelectedJob(job, score(job, context, candidateSkills)))
                .sorted(Comparator.comparingDouble(SelectedJob::prefilterScore).reversed()
                        .thenComparing(item -> Optional.ofNullable(item.job().getPublishedAt())
                                .orElse(item.job().getCreatedAt()), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(properties.getMaxCandidateJobs())
                .toList();
        selected.forEach(item -> {
            item.job().getSkills().size();
            if (item.job().getCompany() != null) {
                item.job().getCompany().getName();
            }
        });
        return selected;
    }

    private double score(Job job, AiJobSearchContext context, Set<String> candidateSkills) {
        Set<String> jobSkills = normalize(job.getSkills());
        long matched = jobSkills.stream().filter(candidateSkills::contains).count();
        double skillScore = jobSkills.isEmpty() ? 0 : 45.0 * matched / jobSkills.size();
        double experienceScore = experienceScore(context.experienceLevel(), job.getExperienceLevel());
        double locationScore = locationScore(context.location(), job.getLocation(), job.getWorkMode());
        double titleScore = titleScore(job.getTitle(), context);
        LocalDateTime publishedAt = Optional.ofNullable(job.getPublishedAt()).orElse(job.getCreatedAt());
        long days = publishedAt == null ? 365 : Math.max(0, ChronoUnit.DAYS.between(publishedAt, LocalDateTime.now()));
        double freshnessScore = Math.max(0, 10.0 * (1.0 - Math.min(days, 180) / 180.0));
        return skillScore + experienceScore + locationScore + titleScore + freshnessScore;
    }

    private double experienceScore(String candidateLevel, String jobLevel) {
        if (jobLevel == null || jobLevel.isBlank()) {
            return 10;
        }
        int candidate = level(candidateLevel);
        int required = level(jobLevel);
        int gap = Math.abs(candidate - required);
        return gap == 0 ? 20 : gap == 1 ? 12 : 3;
    }

    private double locationScore(String candidateLocation, String jobLocation, String workMode) {
        double score = "remote".equalsIgnoreCase(workMode) ? 7 : 0;
        if (candidateLocation != null && jobLocation != null
                && !candidateLocation.isBlank() && !jobLocation.isBlank()) {
            String candidate = candidateLocation.toLowerCase(Locale.ROOT);
            String job = jobLocation.toLowerCase(Locale.ROOT);
            if (candidate.contains(job) || job.contains(candidate)) {
                score += 8;
            }
        }
        return Math.min(15, score);
    }

    private double titleScore(String title, AiJobSearchContext context) {
        if (title == null) {
            return 0;
        }
        String normalizedTitle = title.toLowerCase(Locale.ROOT);
        List<String> terms = new ArrayList<>(context.skills());
        terms.addAll(Arrays.asList(context.headline().split("\\s+")));
        return terms.stream()
                .map(String::trim)
                .filter(term -> term.length() >= 3)
                .map(term -> term.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedTitle::contains) ? 10 : 0;
    }

    private int level(String value) {
        String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT);
        if (normalized.contains("INTERN")) return 0;
        if (normalized.contains("FRESH") || normalized.contains("ENTRY")) return 1;
        if (normalized.contains("JUNIOR")) return 2;
        if (normalized.contains("SENIOR")) return 4;
        if (normalized.contains("LEAD") || normalized.contains("MANAGER")) return 5;
        return 3;
    }

    private Set<String> normalize(Collection<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) {
            values.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank())
                    .map(value -> value.toLowerCase(Locale.ROOT)).forEach(result::add);
        }
        return result;
    }

    public record SelectedJob(Job job, double prefilterScore) {
    }
}
