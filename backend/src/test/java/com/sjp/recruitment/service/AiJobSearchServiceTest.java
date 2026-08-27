package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.JobResponse;
import com.sjp.recruitment.model.dto.request.AiJobSearchRequest;
import com.sjp.recruitment.model.dto.request.AiJobSearchFilters;
import com.sjp.recruitment.model.entity.*;
import com.sjp.recruitment.repository.*;
import com.sjp.recruitment.service.ai.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiJobSearchServiceTest {
    private AuthService authService;
    private CandidateProfileRepository candidateRepository;
    private CandidateAiConsentRepository consentRepository;
    private AiJobSearchRunRepository runRepository;
    private JobRepository jobRepository;
    private FeatureLimitService featureLimitService;
    private AiJobSearchCandidateContextBuilder contextBuilder;
    private AiJobSearchCandidateSelector selector;
    private ShopAiKeyJobSearchClient aiClient;
    private AiJobSearchResultValidator validator;
    private AiJobSearchPersistenceService persistence;
    private JobService jobService;
    private AiJobSearchService service;
    private User user;
    private CandidateProfile candidate;
    private AiJobSearchContext context;
    private final UUID cvId = UUID.randomUUID();
    private final AiJobSearchFilters filters = AiJobSearchFilters.empty();

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        candidateRepository = mock(CandidateProfileRepository.class);
        CandidateCvRepository cvRepository = mock(CandidateCvRepository.class);
        consentRepository = mock(CandidateAiConsentRepository.class);
        runRepository = mock(AiJobSearchRunRepository.class);
        jobRepository = mock(JobRepository.class);
        featureLimitService = mock(FeatureLimitService.class);
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        contextBuilder = mock(AiJobSearchCandidateContextBuilder.class);
        selector = mock(AiJobSearchCandidateSelector.class);
        aiClient = mock(ShopAiKeyJobSearchClient.class);
        validator = mock(AiJobSearchResultValidator.class);
        persistence = mock(AiJobSearchPersistenceService.class);
        jobService = mock(JobService.class);

        AiJobSearchProperties properties = new AiJobSearchProperties();
        properties.setEnabled(true);
        properties.setShopaikeyApiKey("test-key");
        properties.setPromptVersion("ai-job-search-v1");
        service = new AiJobSearchService(
                authService, candidateRepository, cvRepository, consentRepository, runRepository, jobRepository,
                featureLimitService, settingsService, properties, contextBuilder, selector, aiClient, validator,
                persistence, jobService, new SimpleMeterRegistry()
        );

        user = new User();
        user.setId(UUID.randomUUID());
        candidate = new CandidateProfile();
        candidate.setId(UUID.randomUUID());
        candidate.setUser(user);
        CandidateCv cv = new CandidateCv();
        cv.setId(cvId);
        context = new AiJobSearchContext(
                candidate, cv, "candidate-hash", true, List.of("Java"), "Java Developer", "", "Hà Nội",
                2, "JUNIOR", List.of(), List.of(), List.of(), List.of(), "", Map.of()
        );
        CandidateAiConsent consent = new CandidateAiConsent();
        consent.setPolicyVersion("ai-job-search-v1");

        when(authService.getCurrentUser()).thenReturn(user);
        when(candidateRepository.findWithSkillsByUserId(user.getId())).thenReturn(Optional.of(candidate));
        when(settingsService.isAiJobSearchEnabled()).thenReturn(true);
        when(settingsService.getString(eq(SystemSettingsService.AI_JOB_SEARCH_POLICY_VERSION), anyString()))
                .thenReturn("ai-job-search-v1");
        when(consentRepository.findFirstByCandidateIdAndPurposeAndRevokedAtIsNullOrderByGrantedAtDesc(
                candidate.getId(), AiJobSearchPersistenceService.PURPOSE)).thenReturn(Optional.of(consent));
        when(contextBuilder.buildForJobSearch(candidate, cvId)).thenReturn(context);
        when(featureLimitService.getAiJobSearchQuota(user))
                .thenReturn(new FeatureLimitService.AiJobSearchQuota(1, 3, 2, OffsetDateTime.now().plusMonths(1)));
    }

    @Test
    void sameEvaluationFingerprintReusesExactResultEvenWhenForceRefreshIsRequested() {
        Job job = publicJob();
        var selected = selectedJob(job, 80);
        AiJobSearchRun run = succeededRun();
        var stored = new AiJobSearchPersistenceService.StoredRecommendation(
                1, job.getId(), 92, List.of("Java"), List.of("AWS"), "Phù hợp.", true, List.of());

        when(selector.select(context, filters)).thenReturn(List.of(selected));
        when(selector.evaluationHash(context, List.of(selected), filters)).thenReturn("evaluation-hash");
        when(runRepository.findFirstByCandidateIdAndStatusAndInputHashOrderByCreatedAtDesc(
                candidate.getId(), "SUCCEEDED", "evaluation-hash")).thenReturn(Optional.of(run));
        when(persistence.load(candidate.getId(), run.getId())).thenReturn(List.of(stored));
        when(jobRepository.findPublicAiJobIds(anyList(), any(LocalDate.class))).thenReturn(List.of(job.getId()));
        when(jobRepository.findPublicAiJobIds(anyList(), any(LocalDate.class))).thenReturn(List.of(job.getId()));
        when(jobRepository.existsById(job.getId())).thenReturn(true);
        when(jobService.findPublicJobResponseByIdWithoutViewIncrement(job.getId().toString()))
                .thenReturn(mock(JobResponse.class));

        var response = service.search(new AiJobSearchRequest(cvId, true, filters));

        assertTrue(response.cached());
        assertFalse(response.stale());
        assertEquals(run.getId(), response.runId());
        assertEquals(1, response.items().size());
        verifyNoInteractions(aiClient, validator);
        verify(featureLimitService, never()).requireAiJobSearch(any());
        verify(persistence, never()).complete(any(), any(), any(), any());
    }

    @Test
    void providerFailureReturnsProfileFallbackWithoutCompletingOrChargingAiRun() {
        AiJobSearchRun run = new AiJobSearchRun();
        run.setId(UUID.randomUUID());
        Job job = publicJob();
        var selected = selectedJob(job, 80);
        when(selector.select(context, filters)).thenReturn(List.of(selected));
        when(selector.evaluationHash(context, List.of(selected), filters)).thenReturn("changed-hash");
        when(runRepository.findFirstByCandidateIdAndStatusAndInputHashOrderByCreatedAtDesc(
                candidate.getId(), "SUCCEEDED", "changed-hash")).thenReturn(Optional.empty());
        when(persistence.start(context, "changed-hash", filters)).thenReturn(run);
        when(aiClient.rank(any(), any())).thenThrow(new ApiException(
                HttpStatus.BAD_GATEWAY, "AI_JOB_SEARCH_PROVIDER_FAILED", "AI unavailable"));
        stubProfileFallback(job, 71);

        var response = service.search(new AiJobSearchRequest(cvId, false, filters));

        assertEquals("PROFILE_FALLBACK", response.source());
        assertEquals(cvId, response.cvId());
        assertNull(response.runId());
        assertNull(response.expiresAt());
        assertFalse(response.cached());
        assertEquals(1, response.quota().used());
        assertEquals(71, response.items().get(0).matchScore());
        assertEquals(List.of("Java"), response.items().get(0).matchedSkills());
        assertTrue(response.items().get(0).evidence().isEmpty());
        verify(persistence).fail(run.getId(), "AI_JOB_SEARCH_PROVIDER_FAILED");
        verify(persistence, never()).complete(any(), any(), any(), any());
    }

    @Test
    void changedEvaluationFingerprintCreatesNewRun() {
        AiJobSearchRun processingRun = new AiJobSearchRun();
        processingRun.setId(UUID.randomUUID());
        AiJobSearchRun completedRun = succeededRun();
        Job job = publicJob();
        int score = 30;
        var selected = new AiJobSearchCandidateSelector.SelectedJob(job, score);
        var ranked = new AiJobSearchResultValidator.RankedJob(1, job, 94, List.of("Java"), List.of("AWS"), "Phù hợp.", List.of());
        var stored = new AiJobSearchPersistenceService.StoredRecommendation(
                1, job.getId(), 94, List.of("Java"), List.of("AWS"), "Phù hợp.", true, List.of());

        when(selector.select(context, filters)).thenReturn(List.of(selected));
        when(selector.evaluationHash(context, List.of(selected), filters)).thenReturn("new-evaluation-hash");
        when(runRepository.findFirstByCandidateIdAndStatusAndInputHashOrderByCreatedAtDesc(
                candidate.getId(), "SUCCEEDED", "new-evaluation-hash")).thenReturn(Optional.empty());
        when(persistence.start(context, "new-evaluation-hash", filters)).thenReturn(processingRun);
        when(aiClient.rank(context, List.of(selected))).thenReturn(mock(com.fasterxml.jackson.databind.JsonNode.class));
        when(validator.validate(any(), eq(context), eq(List.of(selected)))).thenReturn(List.of(ranked));
        when(persistence.complete(processingRun.getId(), user, context, List.of(ranked))).thenReturn(completedRun);
        when(persistence.load(candidate.getId(), completedRun.getId())).thenReturn(List.of(stored));
        when(jobRepository.existsById(job.getId())).thenReturn(true);
        when(jobService.findPublicJobResponseByIdWithoutViewIncrement(job.getId().toString()))
                .thenReturn(mock(JobResponse.class));

        var response = service.search(new AiJobSearchRequest(cvId, true, filters));

        assertFalse(response.cached());
        assertEquals(cvId, response.cvId());
        assertEquals(completedRun.getId(), response.runId());
        verify(aiClient).rank(context, List.of(selected));
        verify(persistence).complete(processingRun.getId(), user, context, List.of(ranked));
    }

    @Test
    void retriesWithPreviousResponseAndValidationFeedbackBeforePersisting() {
        Job job = publicJob();
        var selected = List.of(selectedJob(job, 80));
        AiJobSearchRun run = prepareUncachedRun(selected);
        var invalid = mock(com.fasterxml.jackson.databind.JsonNode.class);
        var corrected = mock(com.fasterxml.jackson.databind.JsonNode.class);
        var failure = new AiJobSearchValidationException("JOB_QUOTE_NOT_FOUND", "items[0].evidence[0].jobQuote", "Copy exactly.");
        var ranked = List.of(new AiJobSearchResultValidator.RankedJob(1, job, 83, List.of(), List.of(), "Phù hợp.", List.of()));
        when(aiClient.rank(context, selected)).thenReturn(invalid);
        when(validator.validate(invalid, context, selected)).thenThrow(failure);
        when(aiClient.rank(context, selected, invalid, failure)).thenReturn(corrected);
        when(validator.validate(corrected, context, selected)).thenReturn(ranked);
        when(persistence.complete(run.getId(), user, context, ranked)).thenReturn(succeededRun());

        service.search(new AiJobSearchRequest(cvId, false, filters));

        verify(aiClient).rank(context, selected);
        verify(aiClient).rank(context, selected, invalid, failure);
        verifyNoMoreInteractions(aiClient);
        verify(persistence).complete(run.getId(), user, context, ranked);
        verify(persistence, never()).fail(any(), any());
    }

    @Test
    void twoInvalidResponsesReturnProfileFallbackWithoutCompletingOrConsumingQuota() {
        Job job = publicJob();
        var selected = List.of(selectedJob(job, 80));
        AiJobSearchRun run = prepareUncachedRun(selected);
        var failure = new AiJobSearchValidationException("INVALID_JSON", "$", "Return valid JSON.");
        when(aiClient.rank(context, selected)).thenThrow(failure);
        when(aiClient.rank(context, selected, null, failure)).thenThrow(failure);
        stubProfileFallback(job, 63);

        var response = service.search(new AiJobSearchRequest(cvId, false, filters));

        assertEquals("PROFILE_FALLBACK", response.source());
        assertEquals(63, response.items().get(0).matchScore());
        verify(aiClient).rank(context, selected);
        verify(aiClient).rank(context, selected, null, failure);
        verifyNoMoreInteractions(aiClient);
        verify(persistence).fail(run.getId(), "AI_JOB_SEARCH_INVALID_RESPONSE");
        verify(persistence, never()).complete(any(), any(), any(), any());
        verifyNoInteractions(validator);
    }

    @Test
    void repeatedMatchedSkillValidationErrorsAlsoFallBack() {
        Job job = publicJob();
        var selected = List.of(selectedJob(job, 80));
        prepareUncachedRun(selected);
        var invalid = mock(com.fasterxml.jackson.databind.JsonNode.class);
        var failure = new AiJobSearchValidationException("MATCHED_SKILL_NOT_IN_CV", "items[0].matchedSkills[0]", "Invalid skill");
        when(aiClient.rank(context, selected)).thenReturn(invalid);
        when(aiClient.rank(context, selected, invalid, failure)).thenReturn(invalid);
        when(validator.validate(invalid, context, selected)).thenThrow(failure);
        stubProfileFallback(job, 62);
        assertEquals("PROFILE_FALLBACK", service.search(new AiJobSearchRequest(cvId, false, filters)).source());
        verify(validator, times(2)).validate(invalid, context, selected);
        verify(persistence, never()).complete(any(), any(), any(), any());
    }

    @Test
    void fallbackRanksFullCurrentFilteredPoolByProfileNotCvShortlist() {
        Job cvJob = publicJob();
        Job profileJob = publicJob();
        prepareUncachedRun(List.of(selectedJob(cvJob, 99)));
        when(aiClient.rank(any(), any())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "AI_JOB_SEARCH_EMPTY_CONTENT", "empty"));
        stubProfileFallback(cvJob, 20);
        stubProfileFallback(profileJob, 90);
        when(selector.eligibleJobs(filters)).thenReturn(List.of(cvJob, profileJob));
        var response = service.search(new AiJobSearchRequest(cvId, false, filters));
        assertEquals(List.of(90, 20), response.items().stream().map(item -> item.matchScore()).toList());
        assertEquals(List.of(1, 2), response.items().stream().map(item -> item.rank()).toList());
        verify(jobService).profileMatch(candidate, profileJob);
        verify(selector).eligibleJobs(filters);
        verify(contextBuilder, never()).build(any(CandidateProfile.class));
    }

    @Test
    void fallbackCanReturnEmptyPoolIfJobsCloseWhileProviderIsRunning() {
        prepareUncachedRun(List.of(selectedJob(publicJob(), 90)));
        when(aiClient.rank(any(), any())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "AI_JOB_SEARCH_PROVIDER_FAILED", "timeout"));
        when(selector.eligibleJobs(filters)).thenReturn(List.of());
        var response = service.search(new AiJobSearchRequest(cvId, false, filters));
        assertEquals("PROFILE_FALLBACK", response.source());
        assertTrue(response.items().isEmpty());
        verifyNoInteractions(jobService);
    }

    @Test
    void consentRevokedDuringProviderFailureDoesNotReturnFallback() {
        prepareUncachedRun(List.of(selectedJob(publicJob(), 90)));
        CandidateAiConsent consent = new CandidateAiConsent();
        consent.setPolicyVersion("ai-job-search-v1");
        when(consentRepository.findFirstByCandidateIdAndPurposeAndRevokedAtIsNullOrderByGrantedAtDesc(
                candidate.getId(), AiJobSearchPersistenceService.PURPOSE)).thenReturn(Optional.of(consent), Optional.empty());
        when(aiClient.rank(any(), any())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "AI_JOB_SEARCH_PROVIDER_FAILED", "timeout"));
        var failure = assertThrows(ApiException.class, () -> service.search(new AiJobSearchRequest(cvId, false, filters)));
        assertEquals("AI_JOB_SEARCH_CONSENT_REQUIRED", failure.getCode());
        verify(selector, never()).eligibleJobs(any());
    }

    @Test
    void cvAccessAndReadabilityErrorsCannotTriggerFallback() {
        for (String code : List.of("CV_NOT_FOUND", "AI_JOB_SEARCH_CV_UNREADABLE")) {
            doThrow(new ApiException(HttpStatus.NOT_FOUND, code, "invalid CV")).when(contextBuilder).buildForJobSearch(candidate, cvId);
            assertEquals(code, assertThrows(ApiException.class, () -> service.search(new AiJobSearchRequest(cvId, false, filters))).getCode());
        }
        verifyNoInteractions(aiClient, selector, jobService, persistence);
    }

    @Test
    void persistenceFailureAfterValidAiResponseIsNotHiddenByFallback() {
        var selected = List.of(selectedJob(publicJob(), 90));
        AiJobSearchRun run = prepareUncachedRun(selected);
        var response = mock(com.fasterxml.jackson.databind.JsonNode.class);
        when(aiClient.rank(context, selected)).thenReturn(response);
        when(validator.validate(response, context, selected)).thenReturn(List.of());
        when(persistence.complete(eq(run.getId()), eq(user), eq(context), anyList())).thenThrow(new IllegalStateException("database failure"));
        assertEquals("AI_JOB_SEARCH_FAILED", assertThrows(ApiException.class,
                () -> service.search(new AiJobSearchRequest(cvId, false, filters))).getCode());
        verify(selector, never()).eligibleJobs(any());
    }

    @Test
    void profileFallbackReturnsAtMostTenAndKeepsLowConfidenceInformation() {
        var jobs = java.util.stream.IntStream.range(0, 12).mapToObj(i -> publicJob()).toList();
        prepareUncachedRun(List.of(selectedJob(jobs.get(0), 99)));
        when(aiClient.rank(any(), any())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "AI_JOB_SEARCH_PROVIDER_FAILED", "timeout"));
        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            when(jobService.profileMatch(candidate, job)).thenReturn(new JobService.ProfileMatch(i,
                    List.of(), List.of("Java"), "Hoàn thiện hồ sơ kỹ năng để nhận gợi ý chính xác hơn.", true));
            when(jobService.findPublicJobResponseByIdWithoutViewIncrement(job.getId().toString())).thenReturn(mock(JobResponse.class));
        }
        when(selector.eligibleJobs(filters)).thenReturn(jobs);
        var response = service.search(new AiJobSearchRequest(cvId, false, filters));
        assertEquals(10, response.items().size());
        assertEquals(11, response.items().get(0).matchScore());
        assertEquals(2, response.items().get(9).matchScore());
        assertTrue(response.lowConfidence());
        verify(persistence, never()).complete(any(), any(), any(), any());
    }

    @Test
    void httpProviderOutageFallsBackToRealProfileMatchingLogic() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            var providerProperties = new AiJobSearchProperties();
            providerProperties.setShopaikeyBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            providerProperties.setShopaikeyApiKey("test-only");
            var provider = new ShopAiKeyJobSearchClient(org.springframework.web.client.RestClient.builder(),
                    new com.fasterxml.jackson.databind.ObjectMapper(), providerProperties);
            Job job = publicJob();
            Skill skill = new Skill();
            skill.setName("Python");
            JobSkill jobSkill = new JobSkill();
            jobSkill.setSkill(skill);
            job.setJobSkills(List.of(jobSkill));
            job.setTitle("Python Developer");
            candidate.setSkills(List.of("Python"));
            var selected = List.of(selectedJob(job, 0));
            AiJobSearchRun run = prepareUncachedRun(selected);
            when(aiClient.rank(context, selected)).thenAnswer(invocation -> provider.rank(context, selected));
            when(selector.eligibleJobs(filters)).thenReturn(List.of(job));
            when(jobService.profileMatch(candidate, job)).thenCallRealMethod();
            when(jobService.findPublicJobResponseByIdWithoutViewIncrement(job.getId().toString())).thenReturn(mock(JobResponse.class));

            var response = service.search(new AiJobSearchRequest(cvId, false, filters));

            assertEquals("PROFILE_FALLBACK", response.source());
            assertEquals(List.of("Python"), response.items().get(0).matchedSkills());
            assertEquals(74, response.items().get(0).matchScore());
            assertTrue(response.items().get(0).evidence().isEmpty());
            assertEquals(1, calls.get());
            verify(persistence).fail(run.getId(), "AI_JOB_SEARCH_PROVIDER_FAILED");
            verify(persistence, never()).complete(any(), any(), any(), any());
        } finally {
            server.stop(0);
        }
    }

    private void stubProfileFallback(Job job, int score) {
        when(selector.eligibleJobs(filters)).thenReturn(List.of(job));
        when(jobService.profileMatch(candidate, job)).thenReturn(new JobService.ProfileMatch(score,
                List.of("Java"), List.of("Docker"), "Phù hợp vì bạn có Java.", false));
        when(jobService.findPublicJobResponseByIdWithoutViewIncrement(job.getId().toString())).thenReturn(mock(JobResponse.class));
    }

    private AiJobSearchRun prepareUncachedRun(List<AiJobSearchCandidateSelector.SelectedJob> selected) {
        var run = new AiJobSearchRun();
        run.setId(UUID.randomUUID());
        when(selector.select(context, filters)).thenReturn(selected);
        when(selector.evaluationHash(context, selected, filters)).thenReturn("retry-hash");
        when(persistence.start(context, "retry-hash", filters)).thenReturn(run);
        return run;
    }

    private AiJobSearchCandidateSelector.SelectedJob selectedJob(Job job, int matchScore) {
        return new AiJobSearchCandidateSelector.SelectedJob(
                job,
                matchScore
        );
    }


    private AiJobSearchRun succeededRun() {
        AiJobSearchRun run = new AiJobSearchRun();
        run.setId(UUID.randomUUID());
        run.setStatus("SUCCEEDED");
        run.setCvId(cvId);
        run.setCompletedAt(LocalDateTime.now().minusMinutes(2));
        run.setExpiresAt(LocalDateTime.now().plusHours(2));
        return run;
    }


    @Test
    void requiresExplicitCvBeforeAnyProviderOrContextWork() {
        ApiException exception = assertThrows(ApiException.class, () -> service.search(new AiJobSearchRequest(null, false, filters)));
        assertEquals("AI_JOB_SEARCH_CV_REQUIRED", exception.getCode());
        verifyNoInteractions(contextBuilder, aiClient);
    }

    @Test
    void emptyFilteredPoolDoesNotSpendQuotaOrCallProvider() {
        when(selector.select(context, filters)).thenReturn(List.of());
        when(selector.evaluationHash(context, List.of(), filters)).thenReturn("empty");
        var result = service.search(new AiJobSearchRequest(cvId, false, filters));
        assertEquals(cvId, result.cvId());
        assertTrue(result.items().isEmpty());
        verifyNoInteractions(aiClient, persistence);
        verify(featureLimitService, never()).requireAiJobSearch(any());
    }

    @Test
    void expiredCacheIsNotReusedEvenWhenInputIsUnchanged() {
        Job job = publicJob();
        var selected = selectedJob(job, 50);
        AiJobSearchRun expired = succeededRun();
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(selector.select(context, filters)).thenReturn(List.of(selected));
        when(selector.evaluationHash(context, List.of(selected), filters)).thenReturn("same");
        when(runRepository.findFirstByCandidateIdAndStatusAndInputHashOrderByCreatedAtDesc(candidate.getId(), "SUCCEEDED", "same"))
                .thenReturn(Optional.of(expired));
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "PLAN_LIMIT_REACHED", "limit"))
                .when(featureLimitService).requireAiJobSearch(user);
        var exception = assertThrows(ApiException.class, () -> service.search(new AiJobSearchRequest(cvId, false, filters)));
        assertEquals("PLAN_LIMIT_REACHED", exception.getCode());
        verify(persistence, never()).load(any(), any());
        verifyNoInteractions(aiClient);
    }

    @Test
    void statusNeverLoadsAnotherCvsCachedRecommendations() {
        service.status();
        verifyNoInteractions(contextBuilder, selector, runRepository, aiClient);
    }

    @Test
    void selectedCvAndFiltersAreForwardedWithoutFallingBackToDefaultCv() {
        UUID otherCv = UUID.randomUUID();
        var otherFilters = new AiJobSearchFilters("Hà Nội", null, null, null, "hybrid");
        when(contextBuilder.buildForJobSearch(candidate, otherCv)).thenReturn(context);
        when(selector.select(context, otherFilters)).thenReturn(List.of());
        when(selector.evaluationHash(context, List.of(), otherFilters)).thenReturn("other");
        service.search(new AiJobSearchRequest(otherCv, false, otherFilters));
        verify(contextBuilder).buildForJobSearch(candidate, otherCv);
        verify(contextBuilder, never()).build(any(CandidateProfile.class));
        verify(selector).select(context, otherFilters);
    }

    private Job publicJob() {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setStatus("published");
        job.setDeadline(LocalDate.now().plusDays(10));
        Company company = new Company();
        company.setStatus("active");
        job.setCompany(company);
        return job;
    }
}
