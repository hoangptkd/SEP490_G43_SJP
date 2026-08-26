package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.model.dto.request.AiJobSearchFilters;
import com.sjp.recruitment.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiJobSearchCandidateSelector {
    private final JobRepository jobRepository;
    private final AiJobSearchProperties properties;
    private final AiJobMatchScorer scorer;

    @Transactional(readOnly = true)
    public List<SelectedJob> select(AiJobSearchContext context, AiJobSearchFilters filters) {
        List<SelectedJob> selected = eligibleJobs(filters).stream()
                .map(job -> new SelectedJob(job, scorer.shortlistScore(context, job)))
                .sorted(Comparator.comparingInt(SelectedJob::preliminaryScore).reversed()
                        .thenComparing(this::publicationTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.job().getId()))
                .limit(Math.max(1, Math.min(50, Math.min(properties.getMaxCandidateJobs(), properties.getMaxPromptJobs()))))
                .toList();
        selected.forEach(item -> {
            item.job().getSkills().size();
            if (item.job().getCompany() != null) item.job().getCompany().getName();
        });
        return selected;
    }

    /** Full public pool, before CV shortlisting; also used by Profile fallback. */
    @Transactional(readOnly = true)
    public List<Job> eligibleJobs(AiJobSearchFilters filters) {
        return jobRepository.findPublicJobsForAi(LocalDate.now()).stream()
                .filter(job -> matchesFilters(job, filters))
                .toList();
    }

    public String evaluationHash(AiJobSearchContext context, List<SelectedJob> selectedJobs, AiJobSearchFilters filters) {
        StringBuilder canonical = new StringBuilder(context.inputHash());
        canonical.append("|selected-cv-ai-ranking-v4|").append(filters)
                .append('|').append(properties.getScoringVersion())
                .append('|').append(properties.getPromptVersion())
                .append('|').append(properties.getShopaikeyModel())
                .append('|').append(properties.getMaxResults())
                .append('|').append(properties.getMaxCandidateJobs())
                .append('|').append(properties.getMaxPromptJobs());
        selectedJobs.forEach(item -> {
            Job job = item.job();
            canonical.append("\njob:").append(job.getId())
                    .append('|').append(safe(job.getTitle()))
                    .append('|').append(safe(job.getDescription()))
                    .append('|').append(safe(job.getRequirementsText()))
                    .append('|').append(safe(job.getLocation()))
                    .append('|').append(safe(job.getExperienceLevel()))
                    .append('|').append(safe(job.getWorkMode()))
                    .append('|').append(safe(job.getJobType()))
                    .append('|').append(safe(job.getSalaryMin()))
                    .append('|').append(safe(job.getSalaryMax()))
                    .append('|').append(safe(job.getSalaryType()))
                    .append('|').append(safe(job.getCurrency()))
                    .append('|').append(safe(job.getDeadline()))
                    .append('|').append(safe(job.getCompany() == null ? null : job.getCompany().getName()))
                    .append('|').append(item.preliminaryScore());
            job.getSkills().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .sorted()
                    .forEach(skill -> canonical.append("|skill:").append(skill));
        });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private boolean matchesFilters(Job job, AiJobSearchFilters filters) {
        if (filters.location() != null) {
            String location = normalizeLocation(filters.location());
            boolean remote = location.equals("remote") || location.equals("tu xa");
            if (remote ? !"remote".equalsIgnoreCase(job.getWorkMode())
                    : !normalizeLocation(job.getLocation()).contains(location)) return false;
        }
        if (filters.jobType() != null && !filters.jobType().equalsIgnoreCase(job.getJobType())) return false;
        if (filters.workMode() != null && !filters.workMode().equalsIgnoreCase(job.getWorkMode())) return false;
        // Unknown/negotiable salary is retained; it is not evidence of incompatibility.
        if (filters.minSalary() != null && job.getSalaryMax() != null
                && job.getSalaryMax().compareTo(filters.minSalary()) < 0) return false;
        return filters.maxSalary() == null || job.getSalaryMin() == null
                || job.getSalaryMin().compareTo(filters.maxSalary()) <= 0;
    }

    private String normalizeLocation(String value) {
        return java.text.Normalizer.normalize(safe(value).toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd')
                .replaceAll("\\b(thanh pho|tinh|tp\\.?)\\s*", "")
                .replaceAll("\\s+", " ").trim();
    }

    private LocalDateTime publicationTime(SelectedJob item) {
        return Optional.ofNullable(item.job().getPublishedAt()).orElse(item.job().getCreatedAt());
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record SelectedJob(Job job, int preliminaryScore) {}
}
