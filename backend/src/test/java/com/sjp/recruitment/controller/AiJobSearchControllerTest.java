package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.AiJobSearchConsentRequest;
import com.sjp.recruitment.model.dto.request.AiJobSearchRequest;
import com.sjp.recruitment.model.dto.response.AiJobSearchItemResponse;
import com.sjp.recruitment.model.dto.response.AiJobSearchResponse;
import com.sjp.recruitment.model.dto.response.AiJobSearchStatusResponse;
import com.sjp.recruitment.service.AiJobSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiJobSearchControllerTest {

    @Mock private AiJobSearchService aiJobSearchService;
    @InjectMocks private AiJobSearchController controller;

    @Test
    void classLevelPreAuthorizeRequiresCandidateRole() {
        PreAuthorize annotation = AiJobSearchController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation);
        assertEquals("hasRole('CANDIDATE')", annotation.value());
    }

    @Test
    void status_delegatesToService() {
        AiJobSearchStatusResponse expected = mock(AiJobSearchStatusResponse.class);
        when(aiJobSearchService.status()).thenReturn(expected);
        assertSame(expected, controller.status().getBody());
    }

    @Test
    void consent_delegatesToService() {
        AiJobSearchConsentRequest request = mock(AiJobSearchConsentRequest.class);
        AiJobSearchStatusResponse expected = mock(AiJobSearchStatusResponse.class);
        when(aiJobSearchService.consent(request)).thenReturn(expected);
        assertSame(expected, controller.consent(request).getBody());
    }

    @Test
    void revokeConsent_delegatesAndReturnsNoContent() {
        assertEquals(HttpStatus.NO_CONTENT, controller.revokeConsent().getStatusCode());
        verify(aiJobSearchService).revokeConsent();
    }

    @Test
    void search_delegatesWithSelectedCv() {
        AiJobSearchRequest request = new AiJobSearchRequest(UUID.randomUUID(), false, null);
        AiJobSearchResponse expected = mock(AiJobSearchResponse.class);
        when(aiJobSearchService.search(request)).thenReturn(expected);
        assertSame(expected, controller.search(request).getBody());
    }

    @Test
    void search_delegatesWithForceRefresh() {
        AiJobSearchRequest request = new AiJobSearchRequest(UUID.randomUUID(), true, null);
        AiJobSearchResponse expected = mock(AiJobSearchResponse.class);
        when(aiJobSearchService.search(request)).thenReturn(expected);
        assertSame(expected, controller.search(request).getBody());
    }

    @Test
    void recommendation_delegatesWithIds() {
        UUID runId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        AiJobSearchItemResponse expected = mock(AiJobSearchItemResponse.class);
        when(aiJobSearchService.recommendation(runId, jobId)).thenReturn(expected);
        assertSame(expected, controller.recommendation(runId, jobId).getBody());
    }

    @Test
    void httpSearchRejectsMissingCvAndInvalidSalaryRange() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/candidate/ai-job-search/search")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"forceRefresh\":false}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/candidate/ai-job-search/search")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"cvId\":\"" + UUID.randomUUID() + "\",\"filters\":{\"minSalary\":20,\"maxSalary\":10}}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        verifyNoInteractions(aiJobSearchService);
    }

    @Test
    void httpSearchReturnsProfileFallbackAsSuccessfulResponseWithoutAiRunId() throws Exception {
        UUID cvId = UUID.randomUUID();
        when(aiJobSearchService.search(any())).thenReturn(new AiJobSearchResponse("PROFILE_FALLBACK", cvId,
                null, false, false, true, null, null,
                new com.sjp.recruitment.model.dto.response.AiJobSearchQuotaResponse(1, 3, 2, null), java.util.List.of()));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/candidate/ai-job-search/search")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"cvId\":\"" + cvId + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.source").value("PROFILE_FALLBACK"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.cvId").value(cvId.toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.runId").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.quota.used").value(1));
    }

    @Test
    void filtersRoundTripAsStoredJsonAndNormalizeDecimals() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var filters = new com.sjp.recruitment.model.dto.request.AiJobSearchFilters("Hà Nội", new java.math.BigDecimal("20000000.00"), null, null, "hybrid");
        String json = mapper.writeValueAsString(filters);
        assertFalse(json.contains("salaryRangeValid"));
        assertEquals(filters, mapper.readValue(json, com.sjp.recruitment.model.dto.request.AiJobSearchFilters.class));
        assertEquals(com.sjp.recruitment.model.dto.request.AiJobSearchFilters.empty(),
                mapper.readValue("{}", com.sjp.recruitment.model.dto.request.AiJobSearchFilters.class));
    }
}
