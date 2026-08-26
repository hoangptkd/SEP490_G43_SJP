package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.model.entity.*;
import com.sjp.recruitment.model.dto.request.AiJobSearchFilters;
import com.sjp.recruitment.model.dto.response.AiJobSearchItemResponse.Evidence;
import com.sjp.recruitment.repository.AiJobRecommendationRepository;
import com.sjp.recruitment.repository.AiJobSearchRunRepository;
import com.sjp.recruitment.repository.CandidateAiConsentRepository;
import com.sjp.recruitment.service.ai.AiJobSearchContext;
import com.sjp.recruitment.service.ai.AiJobSearchResultValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiJobSearchPersistenceService {
    public static final String PURPOSE = "AI_JOB_SEARCH";

    private final AiJobSearchRunRepository runRepository;
    private final CandidateAiConsentRepository consentRepository;
    private final AiJobRecommendationRepository recommendationRepository;
    private final FeatureLimitService featureLimitService;
    private final AiJobSearchProperties properties;

    @Transactional
    public AiJobSearchRun start(AiJobSearchContext context, String evaluationHash, AiJobSearchFilters filters) {
        LocalDateTime now = LocalDateTime.now();
        runRepository.findFirstByCandidateIdAndStatusOrderByCreatedAtDesc(context.candidate().getId(), "PROCESSING")
                .ifPresent(existing -> {
                    long staleSeconds = Math.max(180L,
                            2L * (properties.getProviderConnectTimeoutMs() + properties.getProviderReadTimeoutMs()) / 1000 + 60);
                    if (existing.getStartedAt() != null && existing.getStartedAt().isBefore(now.minusSeconds(staleSeconds))) {
                        existing.setStatus("FAILED");
                        existing.setFailureCode("AI_JOB_SEARCH_STALE_RUN");
                        existing.setCompletedAt(now);
                        runRepository.saveAndFlush(existing);
                    } else {
                        throw new AiJobSearchInProgressException();
                    }
                });
        AiJobSearchRun run = new AiJobSearchRun();
        run.setCandidate(context.candidate());
        run.setStatus("PROCESSING");
        run.setInputHash(evaluationHash);
        run.setSearchFilters(filters);
        run.setProfileUpdatedAt(context.candidate().getUpdatedAt());
        run.setCvId(context.defaultCv() == null ? null : context.defaultCv().getId());
        run.setCvType(context.defaultCv() == null ? null
                : "builder".equalsIgnoreCase(context.defaultCv().getSourceType()) ? "BUILDER" : "UPLOADED");
        run.setCvUpdatedAt(context.defaultCv() == null ? null : context.defaultCv().getUpdatedAt());
        run.setPromptVersion(properties.getPromptVersion());
        run.setStartedAt(now);
        run.setCreatedAt(now);
        return runRepository.saveAndFlush(run);
    }

    @Transactional
    public AiJobSearchRun complete(
            UUID runId,
            User user,
            AiJobSearchContext context,
            List<AiJobSearchResultValidator.RankedJob> ranked
    ) {
        if (ranked == null || ranked.isEmpty()) {
            throw new IllegalArgumentException("A successful AI job search must contain recommendations");
        }
        AiJobSearchRun run = runRepository.findById(runId).orElseThrow();
        if (!"PROCESSING".equals(run.getStatus())) {
            throw new IllegalStateException("AI job search run is no longer processing");
        }
        LocalDateTime now = LocalDateTime.now();
        List<AiJobRecommendation> recommendations = ranked.stream().map(item -> {
            AiJobRecommendation recommendation = new AiJobRecommendation();
            recommendation.setCandidate(context.candidate());
            recommendation.setJob(item.job());
            recommendation.setRun(run);
            recommendation.setMatchScore(BigDecimal.valueOf(item.matchScore()));
            recommendation.setRankPosition(item.rank());
            recommendation.setModelUsed(properties.getShopaikeyModel());
            recommendation.setGeneratedAt(now);
            recommendation.setReasonJson(Map.of(
                    "matchedSkills", item.matchedSkills(),
                    "missingSkills", item.missingSkills(),
                    "reason", item.reason(),
                    "lowConfidence", context.lowConfidence(),
                    "evidence", item.evidence().stream().map(e -> Map.of("cvQuote", e.cvQuote(), "jobQuote", e.jobQuote())).toList(),
                    "scoreSource", "AI",
                    "scoringVersion", "ai-cv-ranking-v1"
            ));
            return recommendation;
        }).toList();
        recommendationRepository.saveAll(recommendations);
        featureLimitService.consumeAiJobSearch(user);
        run.setStatus("SUCCEEDED");
        run.setModelUsed(properties.getShopaikeyModel());
        run.setResultCount(recommendations.size());
        run.setQuotaConsumed(true);
        run.setCompletedAt(now);
        run.setExpiresAt(now.plusHours(properties.getCacheHours()));
        return runRepository.save(run);
    }

    @Transactional
    public void fail(UUID runId, String code) {
        runRepository.findById(runId).ifPresent(run -> {
            if ("PROCESSING".equals(run.getStatus())) {
                run.setStatus("FAILED");
                run.setFailureCode(truncate(code, 120));
                run.setCompletedAt(LocalDateTime.now());
                runRepository.save(run);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<StoredRecommendation> load(UUID candidateId, UUID runId) {
        return recommendationRepository.findByCandidateIdAndRunIdOrderByRankPositionAsc(candidateId, runId)
                .stream()
                .map(item -> new StoredRecommendation(
                        item.getRankPosition() == null ? 0 : item.getRankPosition(),
                        item.getJob().getId(),
                        item.getMatchScore() == null ? 0 : item.getMatchScore().intValue(),
                        stringList(item.getReasonJson().get("matchedSkills")),
                        stringList(item.getReasonJson().get("missingSkills")),
                        String.valueOf(item.getReasonJson().getOrDefault("reason", "")),
                        Boolean.TRUE.equals(item.getReasonJson().get("lowConfidence")),
                        evidenceList(item.getReasonJson().get("evidence"))
                ))
                .toList();
    }

    @Transactional
    public void grantConsent(CandidateProfile candidate, String policyVersion) {
        LocalDateTime now = LocalDateTime.now();
        consentRepository.findFirstByCandidateIdAndPurposeAndRevokedAtIsNullOrderByGrantedAtDesc(candidate.getId(), PURPOSE)
                .ifPresent(existing -> {
                    existing.setRevokedAt(now);
                    consentRepository.saveAndFlush(existing);
                });
        CandidateAiConsent consent = new CandidateAiConsent();
        consent.setCandidate(candidate);
        consent.setPurpose(PURPOSE);
        consent.setPolicyVersion(policyVersion);
        consent.setGrantedAt(now);
        consent.setCreatedAt(now);
        consentRepository.save(consent);
    }

    @Transactional
    public void revokeConsent(CandidateProfile candidate) {
        consentRepository.findFirstByCandidateIdAndPurposeAndRevokedAtIsNullOrderByGrantedAtDesc(candidate.getId(), PURPOSE)
                .ifPresent(consent -> {
                    consent.setRevokedAt(LocalDateTime.now());
                    consentRepository.save(consent);
                });
        recommendationRepository.deleteByCandidateId(candidate.getId());
    }

    private List<Evidence> evidenceList(Object value) {
        if (!(value instanceof List<?> items)) return List.of();
        return items.stream().filter(item -> item instanceof Map<?, ?>)
                .map(item -> (Map<?, ?>) item)
                .filter(item -> item.get("cvQuote") instanceof String && item.get("jobQuote") instanceof String)
                .map(item -> new Evidence((String) item.get("cvQuote"), (String) item.get("jobQuote")))
                .toList();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().map(String::valueOf).toList();
    }

    private String truncate(String value, int max) {
        String safe = value == null ? "AI_JOB_SEARCH_FAILED" : value;
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    public record StoredRecommendation(
            int rank,
            UUID jobId,
            int matchScore,
            List<String> matchedSkills,
            List<String> missingSkills,
            String reason,
            boolean lowConfidence,
            List<Evidence> evidence
    ) {
    }

    public static class AiJobSearchInProgressException extends RuntimeException {
    }
}
