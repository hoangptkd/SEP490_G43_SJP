package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.model.entity.Job;
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
    public List<SelectedJob> select(AiJobSearchContext context) {
        List<SelectedJob> selected = jobRepository.findPublicJobsForAi(LocalDate.now()).stream()
                .map(job -> new SelectedJob(job, scorer.score(context, job)))
                .sorted(Comparator.comparingInt(SelectedJob::matchScore).reversed()
                        .thenComparing(this::publicationTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.job().getId()))
                .limit(Math.min(Math.max(properties.getMaxResults(), 1), 10))
                .toList();
        selected.forEach(item -> {
            item.job().getSkills().size();
            if (item.job().getCompany() != null) item.job().getCompany().getName();
        });
        return selected;
    }

    public String evaluationHash(AiJobSearchContext context, List<SelectedJob> selectedJobs) {
        StringBuilder canonical = new StringBuilder(context.inputHash());
        canonical.append('|').append(properties.getScoringVersion())
                .append('|').append(properties.getPromptVersion())
                .append('|').append(properties.getShopaikeyModel())
                .append('|').append(properties.getMaxResults());
        selectedJobs.forEach(item -> {
            Job job = item.job();
            canonical.append("\njob:").append(job.getId())
                    .append('|').append(safe(job.getTitle()))
                    .append('|').append(safe(job.getDescription()))
                    .append('|').append(safe(job.getRequirementsText()))
                    .append('|').append(safe(job.getLocation()))
                    .append('|').append(safe(job.getExperienceLevel()))
                    .append('|').append(safe(job.getWorkMode()))
                    .append('|').append(item.matchScore())
                    .append('|').append(item.score().lowConfidenceEvidence());
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

    private LocalDateTime publicationTime(SelectedJob item) {
        return Optional.ofNullable(item.job().getPublishedAt()).orElse(item.job().getCreatedAt());
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record SelectedJob(Job job, AiJobMatchScorer.ScoreBreakdown score) {
        public int matchScore() {
            return score.matchScore();
        }
    }
}
