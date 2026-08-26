package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.AiJobSearchConsentRequest;
import com.sjp.recruitment.model.dto.request.AiJobSearchRequest;
import com.sjp.recruitment.model.dto.request.AiJobSearchFilters;
import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.model.entity.AiJobSearchRun;
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
        boolean hasCv = candidateCvRepository.existsByCandidateIdAndDeletedAtIsNull(candidate.getId());
        String policyVersion = policyVersion();
        return new AiJobSearchStatusResponse(
                enabled(),
                !hasValidConsent(candidate, policyVersion),
                policyVersion,
                new AiJobSearchStatusResponse.Readiness(true, hasCv, !hasCv, hasCv ? List.of() : List.of("cv")),
                quota(authService.getCurrentUser()),
                new AiJobSearchStatusResponse.Cache(
                        false, null, null, false
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

    public AiJobSearchResponse search(AiJobSearchRequest request) {
        requireEnabled();
        if (request == null || request.cvId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_JOB_SEARCH_CV_REQUIRED",
                    "Vui lòng chọn CV để tìm việc bằng AI.");
        }
        User user = authService.getCurrentUser();
        CandidateProfile candidate = candidateProfileRepository.findWithSkillsByUserId(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "AI_JOB_SEARCH_PROFILE_REQUIRED",
                        "Vui lòng tạo hồ sơ ứng viên trước khi sử dụng tìm việc bằng AI."));
        if (!hasValidConsent(candidate, policyVersion())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AI_JOB_SEARCH_CONSENT_REQUIRED",
                    "Vui lòng đồng ý chính sách xử lý dữ liệu AI trước khi tiếp tục.");
        }
        AiJobSearchFilters filters = request.filters();
        AiJobSearchContext context = contextBuilder.buildForJobSearch(candidate, request.cvId());
        List<AiJobSearchCandidateSelector.SelectedJob> candidates = selector.select(context, filters);
        String evaluationHash = selector.evaluationHash(context, candidates, filters);
        Optional<AiJobSearchRun> reusableRun = runRepository
                .findFirstByCandidateIdAndStatusAndInputHashOrderByCreatedAtDesc(
                        candidate.getId(), "SUCCEEDED", evaluationHash);
        if (reusableRun.filter(run -> run.getExpiresAt() != null
                && run.getExpiresAt().isAfter(LocalDateTime.now())
                && request.cvId().equals(run.getCvId())).isPresent()) {
            AiJobSearchRun run = reusableRun.get();
            List<AiJobSearchPersistenceService.StoredRecommendation> stored =
                    publicRecommendations(persistence.load(candidate.getId(), run.getId()));
            if (!stored.isEmpty()) {
                recordOutcome("cache_hit");
                return response(run, stored, true, false, user);
            }
        }
        if (candidates.isEmpty()) {
            recordOutcome("empty_pool");
            return new AiJobSearchResponse("AI", request.cvId(), null, false, false, context.lowConfidence(), null, null, quota(user), List.of());
        }
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
            run = persistence.start(context, evaluationHash, filters);
        } catch (AiJobSearchPersistenceService.AiJobSearchInProgressException | DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "AI_JOB_SEARCH_IN_PROGRESS",
                    "Một lượt tìm việc bằng AI đang được xử lý. Vui lòng chờ hoàn tất.");
        }

        try {
            List<AiJobSearchResultValidator.RankedJob> ranked = null;
            JsonNode previousResponse = null;
            AiJobSearchValidationException validationFailure = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                Timer.Sample providerTimer = Timer.start(meterRegistry);
                try {
                    previousResponse = validationFailure == null
                            ? aiClient.rank(context, candidates)
                            : aiClient.rank(context, candidates, previousResponse, validationFailure);
                    ranked = validator.validate(previousResponse, context, candidates);
                    break;
                } catch (AiJobSearchValidationException exception) {
                    validationFailure = exception;
                    log.warn("AI job search returned invalid output runId={}, attempt={}, code={}, path={}",
                            run.getId(), attempt + 1, exception.getCode(), exception.getPath());
                } finally {
                    providerTimer.stop(meterRegistry.timer("sjp.ai.job.search.provider", "provider", "shopaikey"));
                }
            }
            if (ranked == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_JOB_SEARCH_INVALID_RESPONSE",
                        "AI trả kết quả chưa hợp lệ. Vui lòng thử lại.");
            }
            if (!hasValidConsent(candidate, policyVersion())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "AI_JOB_SEARCH_CONSENT_REQUIRED",
                        "Quyền xử lý dữ liệu AI đã thay đổi. Vui lòng xác nhận lại chính sách.");
            }
            AiJobSearchRun completed = persistence.complete(run.getId(), user, context, ranked);
            log.info("AI job search succeeded runId={}, resultCount={}, model={}, promptVersion={}",
                    completed.getId(), completed.getResultCount(), completed.getModelUsed(), completed.getPromptVersion());
            recordOutcome("success");
            return response(completed, publicRecommendations(persistence.load(candidate.getId(), completed.getId())), false, false, user);
        } catch (ApiException exception) {
            persistence.fail(run.getId(), exception.getCode());
            recordOutcome("failed");
            // Only provider/validation failures qualify, never auth, quota, DB or application errors.
            if (Set.of("AI_JOB_SEARCH_PROVIDER_FAILED", "AI_JOB_SEARCH_EMPTY_RESPONSE",
                    "AI_JOB_SEARCH_EMPTY_CONTENT", "AI_JOB_SEARCH_INVALID_RESPONSE").contains(exception.getCode())) {
                return profileFallback(request, candidate, user, run.getId(), exception.getCode());
            }
            throw exception;
        } catch (RuntimeException exception) {
            persistence.fail(run.getId(), "AI_JOB_SEARCH_FAILED");
            recordOutcome("failed");
            log.warn("AI job search failed runId={}, errorType={}", run.getId(), exception.getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_JOB_SEARCH_FAILED",
                    "AI chưa thể tạo gợi ý việc làm lúc này. Vui lòng thử lại sau.");
        }
    }

    private AiJobSearchResponse profileFallback(AiJobSearchRequest request, CandidateProfile candidate,
                                                User user, UUID failedRunId, String failureCode) {
        if (!hasValidConsent(candidate, policyVersion())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AI_JOB_SEARCH_CONSENT_REQUIRED",
                    "Quyền xử lý dữ liệu đã thay đổi. Vui lòng xác nhận lại chính sách.");
        }
        // Re-query after the provider delay: keep current publication/deadline/filter constraints.
        // Rank the full eligible pool by Profile, not the earlier shortlist selected from the CV.
        var ranked = selector.eligibleJobs(request.filters()).stream()
                .map(job -> new ProfileRankedJob(job.getId(), jobService.profileMatch(candidate, job)))
                .sorted(Comparator.comparingInt((ProfileRankedJob item) -> item.match().matchScore()).reversed()
                        .thenComparing(ProfileRankedJob::jobId))
                .limit(Math.max(1, Math.min(properties.getMaxResults(), 10)))
                .toList();
        List<AiJobSearchItemResponse> items = new ArrayList<>();
        for (ProfileRankedJob item : ranked) {
            var match = item.match();
            items.add(new AiJobSearchItemResponse(items.size() + 1,
                    jobService.findPublicJobResponseByIdWithoutViewIncrement(item.jobId().toString()),
                    match.matchScore(), match.matchedSkills(), match.missingSkills(), match.reason(), List.of()));
        }
        recordOutcome("profile_fallback");
        log.info("Job search profile fallback runId={}, failureCode={}, resultCount={}", failedRunId, failureCode, items.size());
        // The failed AI run remains failed; fallback is not persisted/cached as an AI result or charged.
        return new AiJobSearchResponse("PROFILE_FALLBACK", request.cvId(), null, false, false,
                ranked.stream().anyMatch(item -> item.match().lowConfidence()), LocalDateTime.now(), null,
                quota(user), List.copyOf(items));
    }

    private record ProfileRankedJob(UUID jobId, JobService.ProfileMatch match) {}

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
                        jobService.findPublicJobResponseByIdWithoutViewIncrement(item.jobId().toString()),
                        item.matchScore(),
                        item.matchedSkills(),
                        item.missingSkills(),
                        item.reason(),
                        item.evidence()
                ))
                .toList();
        boolean lowConfidence = stored.stream().anyMatch(AiJobSearchPersistenceService.StoredRecommendation::lowConfidence);
        return new AiJobSearchResponse("AI", run.getCvId(), run.getId(), cached, stale, lowConfidence, run.getCompletedAt(), run.getExpiresAt(), quota(user), items);
    }

    public AiJobSearchItemResponse recommendation(UUID runId, UUID jobId) {
        CandidateProfile candidate = currentCandidate();
        AiJobSearchRun run = runRepository.findByIdAndCandidateIdAndStatus(runId, candidate.getId(), "SUCCEEDED")
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AI_JOB_SEARCH_RESULT_NOT_FOUND",
                        "Không tìm thấy kết quả đánh giá AI."));
        if (run.getCvId() == null || run.getExpiresAt() == null || !run.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "AI_JOB_SEARCH_RESULT_STALE",
                    "Kết quả đã hết hạn. Hãy chọn CV và tìm việc lại.");
        }
        AiJobSearchContext context = contextBuilder.buildForJobSearch(candidate, run.getCvId());
        AiJobSearchFilters filters = run.getSearchFilters() == null ? AiJobSearchFilters.empty() : run.getSearchFilters();
        List<AiJobSearchCandidateSelector.SelectedJob> candidates = selector.select(context, filters);
        if (!Objects.equals(run.getInputHash(), selector.evaluationHash(context, candidates, filters))) {
            throw new ApiException(HttpStatus.CONFLICT, "AI_JOB_SEARCH_RESULT_STALE",
                    "Hồ sơ hoặc thông tin việc làm đã được cập nhật. Hãy cập nhật kết quả AI.");
        }
        AiJobSearchPersistenceService.StoredRecommendation stored = publicRecommendations(
                persistence.load(candidate.getId(), runId)).stream()
                .filter(item -> item.jobId().equals(jobId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AI_JOB_SEARCH_RESULT_NOT_FOUND",
                        "Job này không có trong kết quả đánh giá AI."));
        return new AiJobSearchItemResponse(
                stored.rank(),
                jobService.findPublicJobResponseByIdWithoutViewIncrement(jobId.toString()),
                stored.matchScore(),
                stored.matchedSkills(),
                stored.missingSkills(),
                stored.reason(),
                stored.evidence()
        );
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
