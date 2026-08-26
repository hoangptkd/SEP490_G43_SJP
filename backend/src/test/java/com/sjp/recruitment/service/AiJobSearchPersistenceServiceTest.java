package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.model.dto.request.AiJobSearchFilters;
import com.sjp.recruitment.model.dto.response.AiJobSearchItemResponse.Evidence;
import com.sjp.recruitment.model.entity.*;
import com.sjp.recruitment.repository.*;
import com.sjp.recruitment.service.ai.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiJobSearchPersistenceServiceTest {
    private final AiJobSearchRunRepository runs = mock(AiJobSearchRunRepository.class);
    private final AiJobRecommendationRepository recommendations = mock(AiJobRecommendationRepository.class);
    private final FeatureLimitService limits = mock(FeatureLimitService.class);
    private final AiJobSearchPersistenceService service = new AiJobSearchPersistenceService(runs,
            mock(CandidateAiConsentRepository.class), recommendations, limits, new AiJobSearchProperties());

    @Test
    void runStoresChosenCvAndFilters() {
        var context = context();
        when(runs.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var filters = new AiJobSearchFilters("Hà Nội", null, null, null, "hybrid");
        var run = service.start(context, "hash", filters);
        assertEquals(context.defaultCv().getId(), run.getCvId());
        assertEquals(filters, run.getSearchFilters());
        assertEquals("PROCESSING", run.getStatus());
    }

    @Test
    void storesAiScoreEvidenceAndConsumesOnlyOnce() {
        var context = context();
        var run = new AiJobSearchRun();
        run.setId(UUID.randomUUID());
        run.setStatus("PROCESSING");
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(runs.save(any())).thenAnswer(call -> call.getArgument(0));
        var job = new Job();
        job.setId(UUID.randomUUID());
        var ranked = List.of(new AiJobSearchResultValidator.RankedJob(1, job, 87, List.of("Java"), List.of("Docker"),
                "Phù hợp dự án Java.", List.of(new Evidence("Built Java APIs", "Develop Java APIs"))));
        var user = new User();
        service.complete(run.getId(), user, context, ranked);
        assertEquals("SUCCEEDED", run.getStatus());
        assertTrue(run.isQuotaConsumed());
        assertNotNull(run.getExpiresAt());
        verify(recommendations).saveAll(org.mockito.ArgumentMatchers.argThat(values -> {
            var value = values.iterator().next();
            return value.getMatchScore().intValue() == 87
                    && "AI".equals(value.getReasonJson().get("scoreSource"))
                    && value.getReasonJson().get("evidence") instanceof List<?>;
        }));
        assertThrows(IllegalStateException.class, () -> service.complete(run.getId(), user, context, ranked));
        verify(limits, times(1)).consumeAiJobSearch(user);
    }

    @Test
    void loadsStoredEvidenceWithoutLosingQuotes() {
        var item = new AiJobRecommendation();
        var job = new Job();
        job.setId(UUID.randomUUID());
        item.setJob(job);
        item.setReasonJson(Map.of("reason", "Phù hợp.", "evidence", List.of(Map.of("cvQuote", "CV evidence", "jobQuote", "JD evidence"))));
        UUID candidateId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(recommendations.findByCandidateIdAndRunIdOrderByRankPositionAsc(candidateId, runId)).thenReturn(List.of(item));
        assertEquals(List.of(new Evidence("CV evidence", "JD evidence")), service.load(candidateId, runId).get(0).evidence());
    }

    private AiJobSearchContext context() {
        var context = mock(AiJobSearchContext.class);
        var candidate = new CandidateProfile();
        candidate.setId(UUID.randomUUID());
        var cv = new CandidateCv();
        cv.setId(UUID.randomUUID());
        when(context.candidate()).thenReturn(candidate);
        when(context.defaultCv()).thenReturn(cv);
        return context;
    }
}
