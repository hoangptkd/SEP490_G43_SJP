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
    void search_delegatesWithNullRequest() {
        AiJobSearchResponse expected = mock(AiJobSearchResponse.class);
        when(aiJobSearchService.search(false)).thenReturn(expected);
        assertSame(expected, controller.search(null).getBody());
    }

    @Test
    void search_delegatesWithForceRefresh() {
        AiJobSearchRequest request = mock(AiJobSearchRequest.class);
        when(request.forceRefresh()).thenReturn(true);
        AiJobSearchResponse expected = mock(AiJobSearchResponse.class);
        when(aiJobSearchService.search(true)).thenReturn(expected);
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
}
