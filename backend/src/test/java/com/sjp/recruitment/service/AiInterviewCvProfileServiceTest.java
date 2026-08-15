package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.AiInterviewCvProfileResponse;
import com.sjp.recruitment.model.entity.AiInterviewCvProfile;
import com.sjp.recruitment.model.entity.CandidateCv;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.repository.AiInterviewCvProfileRepository;
import com.sjp.recruitment.service.ai.AiInterviewRateLimiter;
import com.sjp.recruitment.service.ai.AiJobSearchCandidateContextBuilder;
import com.sjp.recruitment.service.ai.AiJobSearchContext;
import com.sjp.recruitment.service.ai.AiProviderException;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInterviewCvProfileServiceTest {

    @Mock private AiInterviewProperties properties;
    @Mock private CandidateService candidateService;
    @Mock private AiJobSearchCandidateContextBuilder contextBuilder;
    @Mock private AiInterviewCvProfileRepository profileRepository;
    @Mock private ShopAiKeyClient shopAiKeyClient;
    @Mock private AiInterviewRateLimiter rateLimiter;

    private AiInterviewCvProfileService service;
    private CandidateProfile candidate;
    private CandidateCv cv;
    private AiJobSearchContext context;

    @BeforeEach
    void setUp() {
        service = new AiInterviewCvProfileService(
                properties, candidateService, contextBuilder, profileRepository,
                shopAiKeyClient, rateLimiter, new ObjectMapper());
        candidate = new CandidateProfile();
        candidate.setId(UUID.randomUUID());
        cv = new CandidateCv();
        cv.setId(UUID.randomUUID());
        cv.setCandidate(candidate);
        cv.setTitle("Backend CV");
        cv.setSourceType("uploaded");
        context = new AiJobSearchContext(
                candidate, cv, "unused", false, List.of("Java"), "Backend Developer", "", "",
                1, "junior", List.of(), List.of(), List.of(), List.of(),
                "Java Spring Boot REST API", Map.of("cvContent", "Java Spring Boot REST API"));
        when(candidateService.getCurrentCandidateProfile()).thenReturn(candidate);
        when(contextBuilder.build(candidate, cv.getId())).thenReturn(context);
        when(properties.getCvProfilePromptVersion()).thenReturn("cv-interview-profile-v1");
        when(properties.getShopaikeyModel()).thenReturn("gpt-test");
    }

    @Test
    void returnsCachedProfileWithoutCallingProvider() {
        CandidateCv detachedCvProxy = mock(CandidateCv.class);
        AiInterviewCvProfile cached = new AiInterviewCvProfile();
        cached.setId(UUID.randomUUID());
        cached.setCandidate(candidate);
        cached.setCv(detachedCvProxy);
        cached.setContentHash("cached-hash");
        cached.setPromptVersion("cv-interview-profile-v1");
        cached.setModelUsed("gpt-test");
        cached.setProfileJson(Map.of(
                "summary", "Backend profile",
                "experienceLevel", "junior",
                "skills", List.of("Java"),
                "suggestedRoles", List.of(Map.of("title", "Backend Developer", "reason", "Có Java")),
                "evidenceClaims", List.of(Map.of("id", "claim-1", "topic", "API", "claim", "Xây REST API"))
        ));
        when(profileRepository
                .findFirstByCandidateIdAndCvIdAndContentHashAndPromptVersionAndModelUsedOrderByCreatedAtDesc(
                        any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(cached));

        AiInterviewCvProfileResponse response = service.analyze(cv.getId().toString());

        assertTrue(response.cached());
        assertEquals(cv.getId().toString(), response.cvId());
        assertEquals(cv.getTitle(), response.cvTitle());
        assertEquals("Backend Developer", response.suggestedRoles().get(0).title());
        verify(shopAiKeyClient, never()).analyzeCvInterviewProfile(any());
        verify(detachedCvProxy, never()).getId();
        verify(detachedCvProxy, never()).getTitle();
    }

    @Test
    void exposesProviderFailureInsteadOfCreatingFallbackProfile() {
        when(profileRepository
                .findFirstByCandidateIdAndCvIdAndContentHashAndPromptVersionAndModelUsedOrderByCreatedAtDesc(
                        any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(shopAiKeyClient.analyzeCvInterviewProfile(Map.of(
                "cvId", cv.getId().toString(),
                "cvTitle", cv.getTitle(),
                "cvContent", context.cvText()
        )))
                .thenThrow(new AiProviderException("AI_PROVIDER_FAILED", "Provider unavailable"));

        ApiException exception = assertThrows(ApiException.class, () -> service.analyze(cv.getId().toString()));

        assertEquals("AI_PROVIDER_FAILED", exception.getCode());
        verify(profileRepository, never()).save(any());
    }
}
