package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.AiJobSearchConsentRequest;
import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.model.entity.AiJobSearchRun;
import com.sjp.recruitment.model.entity.CandidateCv;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.*;
import com.sjp.recruitment.service.ai.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiJobSearchService {
    private final AuthService authService;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateCvRepository candidateCvRepository;
    private final CandidateAiConsentRepository consentRepository;
    private final AiJobSearchRunRepository runRepository;
    private final JobRepository jobRepository;
    private final FeatureLimitService featureLimitService;
    private final SystemSettingsService systemSettingsService;
    private final AiJobSearchProperties properties;
    private final AiJobSearchCandidateContextBuilder contextBuilder;
    private final AiJobSearchCandidateSelector selector;
    private final ShopAiKeyJobSearchClient aiClient;
    private final AiJobSearchResultValidator validator;
    private final AiJobSearchPersistenceService persistence;
    private final JobService jobService;
    private final MeterRegistry meterRegistry;

    public AiJobSearchStatusResponse status() {
        CandidateProfile candidate = currentCandidate();
        CandidateCv defaultCv = defaultCv(candidate);
        String policyVersion = policyVersion();
        AiJobSearchRun latest = runRepository
                .findFirstByCandidateIdAndStatusOrderByCreatedAtDesc(candidate.getId(), "SUCCEEDED")
                .orElse(null);
        boolean fresh = latest != null && isFresh(candidate, defaultCv, latest);
        List<String> missing = new ArrayList<>();
        if (candidate.getHeadline() == null || candidate.getHeadline().isBlank()) missing.add("headline");
        if (candidate.getSkills().isEmpty()) missing.add("skills");
        if (defaultCv == null) missing.add("defaultCv");
        return new AiJobSearchStatusResponse(
                enabled(),
                !hasValidConsent(candidate, policyVersion),
                policyVersion,
                new AiJobSearchStatusResponse.Readiness(true, defaultCv != null, defaultCv == null, List.copyOf(missing)),
                quota(authService.getCurrentUser()),
                new AiJobSearchStatusResponse.Cache(
                        latest != null,
                        latest == null ? null : latest.getCompletedAt(),
                        latest == null ? null : latest.getExpiresAt(),
                        latest != null && !fresh
                )
        );
    }

    public AiJobSearchStatusResponse consent(AiJobSearchConsentRequest request) {
        CandidateProfile candidate = currentCandidate();
        if (!request.accepted()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_JOB_SEARCH_CONSENT_REQUIRED",
                    "Bạn cần đồng ý chính sách để sử dụng tìm việc bằng AI.");
        }
        if (!policyVersion().equals(request.policyVersion())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_JOB_SEARCH_POLICY_OUTDATED",
                    "Chính sách AI đã thay đổi. Vui lòng tải lại và xác nhận phiên bản mới.");
        }
        persistence.grantConsent(candidate, request.policyVersion());
        return status();
    }

    public void revokeConsent() {
        persistence.revokeConsent(currentCandidate());
    }

    public AiJobSearchResponse search(boolean forceRefresh) {
        requireEnabled();
        User user = authService.getCurrentUser();
        CandidateProfile candidate = candidateProfileRepository.findWithSkillsByUserId(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "AI_JOB_SEARCH_PROFILE_REQUIRED",
                        "Vui lòng tạo hồ sơ ứng viên trước khi sử dụng tìm việc bằng AI."));
        if (!hasValidConsent(candidate, policyVersion())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AI_JOB_SEARCH_CONSENT_REQUIRED",
                    "Vui lòng đồng ý chính sách xử lý dữ liệu AI trước khi tiếp tục.");
        }
        if (!forceRefresh) {
            Optional<AiJobSearchRun> previousRun = runRepository
                    .findFirstByCandidateIdAndStatusOrderByCreatedAtDesc(candidate.getId(), "SUCCEEDED");
            if (previousRun.isPresent()) {
                AiJobSearchRun run = previousRun.get();
                List<AiJobSearchPersistenceService.StoredRecommendation> stored =
                        persistence.load(candidate.getId(), run.getId());
                if (!stored.isEmpty()) {
                    boolean stale = !isFresh(candidate, defaultCv(candidate), run);
                    recordOutcome(stale ? "stale_cache_hit" : "cache_hit");
                    return response(run, publicRecommendations(stored), true, stale, user);
                }
            }
        }
        AiJobSearchContext context = contextBuilder.build(candidate);
        try {
            featureLimitService.requireAiJobSearch(user);
        } catch (ApiException exception) {
            if ("PLAN_LIMIT_REACHED".equals(exception.getCode())) {
                recordOutcome("quota_denied");
            }
            throw exception;
        }
        AiJobSearchRun run;
        try {
            run = persistence.start(context);
        } catch (AiJobSearchPersistenceService.AiJobSearchInProgressException | DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "AI_JOB_SEARCH_IN_PROGRESS",
                    "Một lượt tìm việc bằng AI đang được xử lý. Vui lòng chờ hoàn tất.");
        }

        try {
            List<AiJobSearchCandidateSelector.SelectedJob> candidates = selector.select(context);
            if (candidates.isEmpty()) {
                persistence.fail(run.getId(), "AI_JOB_SEARCH_EMPTY_POOL");
                recordOutcome("empty_pool");
                return new AiJobSearchResponse("AI", false, false, context.lowConfidence(), null, null, quota(user), List.of());
            }
            List<AiJobSearchResultValidator.RankedJob> ranked = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                Timer.Sample providerTimer = Timer.start(meterRegistry);
                try {
                    ranked = validator.validate(aiClient.rank(context, candidates), context, candidates);
                    break;
                } catch (AiJobSearchValidationException exception) {
                    log.warn("AI job search returned invalid output runId={}, attempt={}", run.getId(), attempt + 1);
                } finally {
                    providerTimer.stop(meterRegistry.timer("sjp.ai.job.search.provider", "provider", "shopaikey"));
                }
            }
            if (ranked == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_JOB_SEARCH_INVALID_RESPONSE",
                        "AI trả kết quả chưa hợp lệ. Vui lòng thử lại.");
            }
            AiJobSearchRun completed = persistence.complete(run.getId(), user, context, ranked);
            log.info("AI job search succeeded runId={}, resultCount={}, model={}, promptVersion={}",
                    completed.getId(), completed.getResultCount(), completed.getModelUsed(), completed.getPromptVersion());
            recordOutcome("success");
            return response(completed, persistence.load(candidate.getId(), completed.getId()), false, false, user);
        } catch (ApiException exception) {
            persistence.fail(run.getId(), exception.getCode());
            recordOutcome("failed");
            throw exception;
        } catch (RuntimeException exception) {
            persistence.fail(run.getId(), "AI_JOB_SEARCH_FAILED");
            recordOutcome("failed");
            log.warn("AI job search failed runId={}, errorType={}", run.getId(), exception.getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_JOB_SEARCH_FAILED",
                    "AI chưa thể tạo gợi ý việc làm lúc này. Vui lòng thử lại sau.");
        }
    }

    private AiJobSearchResponse response(
            AiJobSearchRun run,
            List<AiJobSearchPersistenceService.StoredRecommendation> stored,
            boolean cached,
            boolean stale,
            User user
    ) {
        List<AiJobSearchItemResponse> items = stored.stream()
                .filter(item -> jobRepository.existsById(item.jobId()))
                .map(item -> new AiJobSearchItemResponse(
                        item.rank(),
                        jobService.findJobResponseById(item.jobId().toString()),
                        item.matchScore(),
                        item.matchedSkills(),
                        item.missingSkills(),
                        item.reason()
                ))
                .toList();
        boolean lowConfidence = stored.stream().anyMatch(AiJobSearchPersistenceService.StoredRecommendation::lowConfidence);
        return new AiJobSearchResponse("AI", cached, stale, lowConfidence, run.getCompletedAt(), run.getExpiresAt(), quota(user), items);
    }

    private List<AiJobSearchPersistenceService.StoredRecommendation> publicRecommendations(
            List<AiJobSearchPersistenceService.StoredRecommendation> stored
    ) {
        if (stored.isEmpty()) return List.of();
        Set<UUID> publicJobIds = new HashSet<>(jobRepository.findPublicAiJobIds(
                stored.stream().map(AiJobSearchPersistenceService.StoredRecommendation::jobId).toList(),
                LocalDate.now()
        ));
        return stored.stream().filter(item -> publicJobIds.contains(item.jobId())).toList();
    }

    private boolean isFresh(CandidateProfile candidate, CandidateCv defaultCv, AiJobSearchRun run) {
        return run.getExpiresAt() != null
                && run.getExpiresAt().isAfter(LocalDateTime.now())
                && Objects.equals(run.getProfileUpdatedAt(), candidate.getUpdatedAt())
                && Objects.equals(run.getCvId(), defaultCv == null ? null : defaultCv.getId())
                && Objects.equals(run.getCvUpdatedAt(), defaultCv == null ? null : defaultCv.getUpdatedAt());
    }

    private boolean enabled() {
        return properties.isEnabled() && properties.isProviderConfigured() && systemSettingsService.isAiJobSearchEnabled();
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_JOB_SEARCH_UNAVAILABLE",
                    "Tìm việc bằng AI hiện chưa sẵn sàng. Vui lòng thử lại sau.");
        }
    }

    private CandidateProfile currentCandidate() {
        User user = authService.getCurrentUser();
        return candidateProfileRepository.findWithSkillsByUserId(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "AI_JOB_SEARCH_PROFILE_REQUIRED",
                        "Vui lòng tạo hồ sơ ứng viên trước khi sử dụng tìm việc bằng AI."));
    }

    private CandidateCv defaultCv(CandidateProfile candidate) {
        return candidateCvRepository.findFirstByCandidateIdAndDefaultCvTrueAndDeletedAtIsNullOrderByUpdatedAtDesc(candidate.getId())
                .orElse(null);
    }

    private boolean hasValidConsent(CandidateProfile candidate, String policyVersion) {
        return consentRepository.findFirstByCandidateIdAndPurposeAndRevokedAtIsNullOrderByGrantedAtDesc(
                        candidate.getId(), AiJobSearchPersistenceService.PURPOSE)
                .filter(consent -> policyVersion.equals(consent.getPolicyVersion()))
                .isPresent();
    }

    private String policyVersion() {
        return systemSettingsService.getString(SystemSettingsService.AI_JOB_SEARCH_POLICY_VERSION,
                properties.getPromptVersion());
    }

    private AiJobSearchQuotaResponse quota(User user) {
        FeatureLimitService.AiJobSearchQuota quota = featureLimitService.getAiJobSearchQuota(user);
        return new AiJobSearchQuotaResponse(quota.used(), quota.limit(), quota.remaining(), quota.resetAt());
    }

    private void recordOutcome(String outcome) {
        meterRegistry.counter("sjp.ai.job.search.requests", "outcome", outcome).increment();
    }
}
