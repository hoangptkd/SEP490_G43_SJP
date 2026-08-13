package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.JobResponse;
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
        context = new AiJobSearchContext(
                candidate, null, "candidate-hash", true, List.of("Java"), "Java Developer", "", "Hà Nội",
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
        when(contextBuilder.build(candidate)).thenReturn(context);
        when(featureLimitService.getAiJobSearchQuota(user))
                .thenReturn(new FeatureLimitService.AiJobSearchQuota(1, 3, 2, OffsetDateTime.now().plusMonths(1)));
    }

    @Test
    void sameEvaluationFingerprintReusesExactResultEvenWhenForceRefreshIsRequested() {
        Job job = publicJob();
        var selected = selectedJob(job, 80);
        AiJobSearchRun run = succeededRun();
        var stored = new AiJobSearchPersistenceService.StoredRecommendation(
                1, job.getId(), 92, List.of("Java"), List.of("AWS"), "Phù hợp.", true);

        when(selector.select(context)).thenReturn(List.of(selected));
        when(selector.evaluationHash(context, List.of(selected))).thenReturn("evaluation-hash");
        when(runRepository.findFirstByCandidateIdAndStatusAndInputHashOrderByCreatedAtDesc(
                candidate.getId(), "SUCCEEDED", "evaluation-hash")).thenReturn(Optional.of(run));
        when(persistence.load(candidate.getId(), run.getId())).thenReturn(List.of(stored));
        when(jobRepository.findPublicAiJobIds(anyList(), any(LocalDate.class))).thenReturn(List.of(job.getId()));
        when(jobRepository.existsById(job.getId())).thenReturn(true);
        when(jobService.findPublicJobResponseByIdWithoutViewIncrement(job.getId().toString()))
                .thenReturn(mock(JobResponse.class));

        var response = service.search(true);

        assertTrue(response.cached());
        assertFalse(response.stale());
        assertEquals(run.getId(), response.runId());
        assertEquals(1, response.items().size());
        verifyNoInteractions(aiClient, validator);
        verify(featureLimitService, never()).requireAiJobSearch(any());
        verify(persistence, never()).complete(any(), any(), any(), any());
    }

    @Test
    void providerFailureMarksRunFailedAndDoesNotConsumeResult() {
        AiJobSearchRun run = new AiJobSearchRun();
        run.setId(UUID.randomUUID());
        Job job = publicJob();
        var selected = selectedJob(job, 80);
        when(selector.select(context)).thenReturn(List.of(selected));
        when(selector.evaluationHash(context, List.of(selected))).thenReturn("changed-hash");
        when(runRepository.findFirstByCandidateIdAndStatusAndInputHashOrderByCreatedAtDesc(
                candidate.getId(), "SUCCEEDED", "changed-hash")).thenReturn(Optional.empty());
        when(persistence.start(context, "changed-hash")).thenReturn(run);
        when(aiClient.rank(any(), any())).thenThrow(new ApiException(
                HttpStatus.BAD_GATEWAY, "AI_JOB_SEARCH_PROVIDER_FAILED", "AI unavailable"));

        ApiException exception = assertThrows(ApiException.class, () -> service.search(false));

        assertEquals("AI_JOB_SEARCH_PROVIDER_FAILED", exception.getCode());
        verify(persistence).fail(run.getId(), "AI_JOB_SEARCH_PROVIDER_FAILED");
        verify(persistence, never()).complete(any(), any(), any(), any());
    }

    @Test
    void changedEvaluationFingerprintCreatesNewRun() {
        AiJobSearchRun processingRun = new AiJobSearchRun();
        processingRun.setId(UUID.randomUUID());
        AiJobSearchRun completedRun = succeededRun();
        Job job = publicJob();
        var score = scoreBreakdown(94, List.of("Java"), List.of("AWS"));
        var selected = new AiJobSearchCandidateSelector.SelectedJob(job, score);
        var ranked = new AiJobSearchResultValidator.RankedJob(1, job, score, "Phù hợp.");
        var stored = new AiJobSearchPersistenceService.StoredRecommendation(
                1, job.getId(), 94, List.of("Java"), List.of("AWS"), "Phù hợp.", true);

        when(selector.select(context)).thenReturn(List.of(selected));
        when(selector.evaluationHash(context, List.of(selected))).thenReturn("new-evaluation-hash");
        when(runRepository.findFirstByCandidateIdAndStatusAndInputHashOrderByCreatedAtDesc(
                candidate.getId(), "SUCCEEDED", "new-evaluation-hash")).thenReturn(Optional.empty());
        when(persistence.start(context, "new-evaluation-hash")).thenReturn(processingRun);
        when(aiClient.rank(context, List.of(selected))).thenReturn(mock(com.fasterxml.jackson.databind.JsonNode.class));
        when(validator.validate(any(), eq(context), eq(List.of(selected)))).thenReturn(List.of(ranked));
        when(persistence.complete(processingRun.getId(), user, context, List.of(ranked))).thenReturn(completedRun);
        when(persistence.load(candidate.getId(), completedRun.getId())).thenReturn(List.of(stored));
        when(jobRepository.existsById(job.getId())).thenReturn(true);
        when(jobService.findPublicJobResponseByIdWithoutViewIncrement(job.getId().toString()))
                .thenReturn(mock(JobResponse.class));

        var response = service.search(true);

        assertFalse(response.cached());
        assertEquals(completedRun.getId(), response.runId());
        verify(aiClient).rank(context, List.of(selected));
        verify(persistence).complete(processingRun.getId(), user, context, List.of(ranked));
    }

    private AiJobSearchCandidateSelector.SelectedJob selectedJob(Job job, int matchScore) {
        return new AiJobSearchCandidateSelector.SelectedJob(
                job,
                scoreBreakdown(matchScore, List.of("Java"), List.of("AWS"))
        );
    }

    private AiJobMatchScorer.ScoreBreakdown scoreBreakdown(int matchScore, List<String> matched, List<String> missing) {
        return new AiJobMatchScorer.ScoreBreakdown(
                matchScore, 0.8, 0.7, 0.6, 0.5, 0.4, matched, missing, false
        );
    }

    private AiJobSearchRun succeededRun() {
        AiJobSearchRun run = new AiJobSearchRun();
        run.setId(UUID.randomUUID());
        run.setStatus("SUCCEEDED");
        run.setCompletedAt(LocalDateTime.now().minusMinutes(2));
        run.setExpiresAt(LocalDateTime.now().plusHours(2));
        return run;
    }

    private AiJobMatchScorer.ScoreBreakdown score(int matchScore, List<String> matched, List<String> missing) {
        return new AiJobMatchScorer.ScoreBreakdown(matchScore, 20.0, 20.0, 15.0, 15.0, 10.0, matched, missing, false);
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
