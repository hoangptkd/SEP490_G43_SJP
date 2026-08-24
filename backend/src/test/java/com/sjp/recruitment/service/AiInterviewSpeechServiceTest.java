package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.dto.response.AiInterviewSpeechTicketResponse;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.service.ai.AiInterviewRateLimiter;
import com.sjp.recruitment.service.ai.AiInterviewSpeechCache;
import com.sjp.recruitment.service.ai.AiInterviewSpeechPrefetchService;
import com.sjp.recruitment.service.ai.ShopAiKeyGeminiTtsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiInterviewSpeechServiceTest {

    private CandidateService candidateService;
    private ShopAiKeyGeminiTtsClient ttsClient;
    private AiInterviewSpeechCache cache;
    private AiInterviewSpeechPrefetchService prefetchService;
    private AiInterviewSpeechService service;

    @BeforeEach
    void setUp() {
        candidateService = mock(CandidateService.class);
        AiInterviewRateLimiter rateLimiter = mock(AiInterviewRateLimiter.class);
        ttsClient = mock(ShopAiKeyGeminiTtsClient.class);
        cache = mock(AiInterviewSpeechCache.class);
        prefetchService = mock(AiInterviewSpeechPrefetchService.class);

        AiInterviewProperties properties = new AiInterviewProperties();
        properties.setGladiaApiKey("gladia-test-key");
        properties.getTextAi().setApiKey("text-ai-test-key");
        properties.setShopaikeyApiKey("shopaikey-test-key");
        properties.setVoiceProvider("shopaikey_gemini_stream");

        CandidateProfile candidate = new CandidateProfile();
        candidate.setId(UUID.randomUUID());
        when(candidateService.getCurrentCandidateProfile()).thenReturn(candidate);
        when(ttsClient.isConfigured()).thenReturn(true);
        when(ttsClient.contentType()).thenReturn(ShopAiKeyGeminiTtsClient.CONTENT_TYPE);
        when(cache.key(any())).thenReturn("cache-key");

        service = new AiInterviewSpeechService(
                candidateService,
                rateLimiter,
                ttsClient,
                cache,
                prefetchService,
                properties,
                new ObjectMapper()
        );
    }

    @Test
    void waitsBrieflyForSharedPrefetchThenStreamsAudioAndDoneEvents() {
        byte[] pcm = new byte[]{0, 0, 1, 0};
        when(prefetchService.prefetchAndAwait("Câu hỏi Java", 1_200)).thenReturn(pcm);

        AiInterviewSpeechTicketResponse ticket = service.createTicket("Câu hỏi Java");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.streamSpeech(token(ticket), output);

        String sse = output.toString(StandardCharsets.UTF_8);
        assertThat(ticket.contentType()).startsWith("text/event-stream");
        assertThat(sse).contains("event: audio", "\"sequence\":0", "event: done")
                .doesNotContain("event: error");
        verify(prefetchService).prefetchAndAwait("Câu hỏi Java", 1_200);
    }

    @Test
    void streamsGeminiDirectlyAndCachesAudioWhenPrefetchIsNotReady() {
        byte[] pcm = new byte[]{2, 0, 3, 0};
        when(prefetchService.prefetchAndAwait("Câu hỏi Java", 1_200)).thenReturn(null);
        doAnswer(invocation -> {
            ShopAiKeyGeminiTtsClient.AudioChunkConsumer consumer = invocation.getArgument(1);
            consumer.accept(pcm, ShopAiKeyGeminiTtsClient.CONTENT_TYPE);
            return new ShopAiKeyGeminiTtsClient.StreamMetrics(
                    pcm.length, 20, 40, ShopAiKeyGeminiTtsClient.CONTENT_TYPE);
        }).when(ttsClient).streamSpeech(anyString(),
                any(ShopAiKeyGeminiTtsClient.AudioChunkConsumer.class));

        AiInterviewSpeechTicketResponse ticket = service.createTicket("Câu hỏi Java");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.streamSpeech(token(ticket), output);

        String sse = output.toString(StandardCharsets.UTF_8);
        assertThat(sse).contains("event: audio", "event: done", "\"audioBytes\":4")
                .doesNotContain("event: error", "GEMINI_TTS_CACHE_NOT_READY");
        verify(ttsClient).streamSpeech(eq("Câu hỏi Java"),
                any(ShopAiKeyGeminiTtsClient.AudioChunkConsumer.class));
        verify(cache).put("cache-key", pcm);
    }

    @Test
    void reportsProviderFailureOnlyWhenDirectGeminiStreamActuallyFails() {
        when(prefetchService.prefetchAndAwait("Câu hỏi Java", 1_200)).thenReturn(null);
        doThrow(new com.sjp.recruitment.service.ai.AiProviderException(
                "GEMINI_TTS_FIRST_AUDIO_TIMEOUT", "Gemini TTS timeout"))
                .when(ttsClient).streamSpeech(anyString(),
                        any(ShopAiKeyGeminiTtsClient.AudioChunkConsumer.class));

        AiInterviewSpeechTicketResponse ticket = service.createTicket("Câu hỏi Java");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.streamSpeech(token(ticket), output);

        String sse = output.toString(StandardCharsets.UTF_8);
        assertThat(sse).contains("event: error", "GEMINI_TTS_FIRST_AUDIO_TIMEOUT")
                .doesNotContain("GEMINI_TTS_CACHE_NOT_READY", "event: audio", "event: done");
        verify(cache, never()).put(anyString(), any(byte[].class));
    }

    @Test
    void wrapsCachedPcmWithAudioAndDoneWithoutCallingProvider() {
        byte[] pcm = new byte[]{0, 0, 1, 0};
        when(cache.getSpeech("Câu hỏi Java")).thenReturn(pcm);

        AiInterviewSpeechTicketResponse ticket = service.createTicket("Câu hỏi Java");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.streamSpeech(token(ticket), output);

        String sse = output.toString(StandardCharsets.UTF_8);
        assertThat(sse).contains("event: audio", "event: done", "\"audioBytes\":4")
                .doesNotContain("event: error");
        verify(ttsClient, never()).streamSpeech(anyString(),
                any(ShopAiKeyGeminiTtsClient.AudioChunkConsumer.class));
    }

    private String token(AiInterviewSpeechTicketResponse ticket) {
        return ticket.streamUrl().substring(ticket.streamUrl().lastIndexOf('/') + 1);
    }
}
