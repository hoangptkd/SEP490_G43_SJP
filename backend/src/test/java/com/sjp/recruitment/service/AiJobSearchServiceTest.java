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
    private CandidateCvRepository cvRepository;
    private CandidateAiConsentRepository consentRepository;
    private AiJobSearchRunRepository runRepository;
    private JobRepository jobRepository;
    private FeatureLimitService featureLimitService;
    private SystemSettingsService settingsService;
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
        cvRepository = mock(CandidateCvRepository.class);
        consentRepository = mock(CandidateAiConsentRepository.class);
        runRepository = mock(AiJobSearchRunRepository.class);
        jobRepository = mock(JobRepository.class);
        featureLimitService = mock(FeatureLimitService.class);
        settingsService = mock(SystemSettingsService.class);
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
                candidate, null, "input-hash", true, List.of("Java"), "Java Developer", "", "Hà Nội",
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
    void previousResultDoesNotCallProviderOrConsumeQuotaEvenWhenStale() {
        AiJobSearchRun run = succeededRun();
        run.setExpiresAt(LocalDateTime.now().minusHours(1));
        run.setProfileUpdatedAt(LocalDateTime.now().minusDays(1));
        candidate.setUpdatedAt(LocalDateTime.now());
        Job job = publicJob();
        var stored = new AiJobSearchPersistenceService.StoredRecommendation(
                1, job.getId(), 92, List.of("Java"), List.of("AWS"), "Phù hợp.", true);
        when(runRepository.findFirstByCandidateIdAndStatusOrderByCreatedAtDesc(candidate.getId(), "SUCCEEDED"))
                .thenReturn(Optional.of(run));
        when(persistence.load(candidate.getId(), run.getId())).thenReturn(List.of(stored));
        when(jobRepository.findPublicAiJobIds(anyList(), any(LocalDate.class))).thenReturn(List.of(job.getId()));
        when(jobRepository.existsById(job.getId())).thenReturn(true);
        when(jobService.findJobResponseById(job.getId().toString())).thenReturn(mock(JobResponse.class));

        var response = service.search(false);

        assertTrue(response.cached());
        assertTrue(response.stale());
        assertEquals(1, response.items().size());
        verifyNoInteractions(selector, aiClient, validator);
        verifyNoInteractions(contextBuilder);
        verify(featureLimitService, never()).requireAiJobSearch(any());
        verify(persistence, never()).complete(any(), any(), any(), any());
    }

    @Test
    void providerFailureMarksRunFailedAndDoesNotComplete() {
        AiJobSearchRun run = new AiJobSearchRun();
        run.setId(UUID.randomUUID());
        Job job = publicJob();
        when(runRepository.findFirstByCandidateIdAndStatusOrderByCreatedAtDesc(candidate.getId(), "SUCCEEDED"))
                .thenReturn(Optional.empty());
        when(persistence.start(context)).thenReturn(run);
        when(selector.select(context)).thenReturn(List.of(new AiJobSearchCandidateSelector.SelectedJob(job, 80)));
        when(aiClient.rank(any(), any())).thenThrow(new ApiException(
                HttpStatus.BAD_GATEWAY, "AI_JOB_SEARCH_PROVIDER_FAILED", "AI unavailable"));

        ApiException exception = assertThrows(ApiException.class, () -> service.search(false));

        assertEquals("AI_JOB_SEARCH_PROVIDER_FAILED", exception.getCode());
        verify(persistence).fail(run.getId(), "AI_JOB_SEARCH_PROVIDER_FAILED");
        verify(persistence, never()).complete(any(), any(), any(), any());
    }

    @Test
    void forceRefreshCreatesANewAiRun() {
        AiJobSearchRun processingRun = new AiJobSearchRun();
        processingRun.setId(UUID.randomUUID());
        AiJobSearchRun completedRun = succeededRun();
        Job job = publicJob();
        var selected = new AiJobSearchCandidateSelector.SelectedJob(job, 80);
        var ranked = new AiJobSearchResultValidator.RankedJob(
                1, job, 94, List.of("Java"), List.of("AWS"), "Phù hợp.");
        var stored = new AiJobSearchPersistenceService.StoredRecommendation(
                1, job.getId(), 94, List.of("Java"), List.of("AWS"), "Phù hợp.", true);

        when(persistence.start(context)).thenReturn(processingRun);
        when(selector.select(context)).thenReturn(List.of(selected));
        when(aiClient.rank(context, List.of(selected))).thenReturn(mock(com.fasterxml.jackson.databind.JsonNode.class));
        when(validator.validate(any(), eq(context), eq(List.of(selected)))).thenReturn(List.of(ranked));
        when(persistence.complete(processingRun.getId(), user, context, List.of(ranked))).thenReturn(completedRun);
        when(persistence.load(candidate.getId(), completedRun.getId())).thenReturn(List.of(stored));
        when(jobRepository.existsById(job.getId())).thenReturn(true);
        when(jobService.findJobResponseById(job.getId().toString())).thenReturn(mock(JobResponse.class));

        var response = service.search(true);

        assertFalse(response.cached());
        assertFalse(response.stale());
        assertEquals(1, response.items().size());
        verify(contextBuilder).build(candidate);
        verify(aiClient).rank(context, List.of(selected));
        verify(persistence).complete(processingRun.getId(), user, context, List.of(ranked));
        verify(runRepository, never())
                .findFirstByCandidateIdAndStatusOrderByCreatedAtDesc(candidate.getId(), "SUCCEEDED");
    }

    private AiJobSearchRun succeededRun() {
        AiJobSearchRun run = new AiJobSearchRun();
        run.setId(UUID.randomUUID());
        run.setStatus("SUCCEEDED");
        run.setCompletedAt(LocalDateTime.now().minusMinutes(2));
        run.setExpiresAt(LocalDateTime.now().plusHours(2));
        return run;
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
