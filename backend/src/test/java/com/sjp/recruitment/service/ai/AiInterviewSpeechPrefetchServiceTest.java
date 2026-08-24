package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiInterviewProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiInterviewSpeechPrefetchServiceTest {

    private ShopAiKeyGeminiTtsClient ttsClient;
    private AiInterviewSpeechPrefetchService service;
    private AiInterviewProperties properties;
    private AtomicLong nanoTime;

    @BeforeEach
    void setUp() {
        properties = new AiInterviewProperties();
        AiInterviewSpeechCache cache = new AiInterviewSpeechCache(properties);
        ttsClient = mock(ShopAiKeyGeminiTtsClient.class);
        when(ttsClient.isConfigured()).thenReturn(true);
        nanoTime = new AtomicLong();
        service = new AiInterviewSpeechPrefetchService(
                ttsClient, cache, properties, nanoTime::get);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void prefetchesSegmentsSequentiallyAndReturnsComposedConversationAudio() throws Exception {
        List<String> callOrder = new CopyOnWriteArrayList<>();
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger maximumActiveCalls = new AtomicInteger();
        doAnswer(invocation -> {
            String text = invocation.getArgument(0);
            int active = activeCalls.incrementAndGet();
            maximumActiveCalls.accumulateAndGet(active, Math::max);
            callOrder.add(text);
            OutputStream output = invocation.getArgument(1);
            output.write(text.startsWith("Chào") ? new byte[]{1, 0} : new byte[]{2, 0});
            activeCalls.decrementAndGet();
            return new ShopAiKeyGeminiTtsClient.StreamMetrics(2, 10, 20,
                    ShopAiKeyGeminiTtsClient.CONTENT_TYPE);
        }).when(ttsClient).streamSpeech(anyString(), any(OutputStream.class));

        byte[] audio = service.prefetchSegmentsAndAwait(
                "Chào bạn.\nBạn hãy giới thiệu kinh nghiệm Java.", 1_000);

        assertThat(audio).containsExactly(1, 0, 2, 0);
        assertThat(callOrder).containsExactly(
                "Chào bạn.", "Bạn hãy giới thiệu kinh nghiệm Java.");
        assertThat(maximumActiveCalls).hasValue(1);
    }

    @Test
    void doesNotRetryAtPrefetchLayerAndSkipsQueuedCallsDuringCooldown() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new AiProviderException("GEMINI_TTS_FIRST_AUDIO_TIMEOUT", "timeout");
        }).when(ttsClient).streamSpeech(anyString(), any(OutputStream.class));

        service.prefetch(List.of("Câu hỏi đầu tiên", "Câu hỏi thứ hai"));

        assertThat(service.awaitReady("Câu hỏi đầu tiên", 1_000)).isNull();
        assertThat(service.awaitReady("Câu hỏi thứ hai", 1_000)).isNull();
        assertThat(attempts).hasValue(1);
        verify(ttsClient, times(1)).streamSpeech(anyString(), any(OutputStream.class));
    }

    @Test
    void allowsALaterPrefetchAfterCooldownExpires() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                throw new AiProviderException("GEMINI_TTS_FIRST_AUDIO_TIMEOUT", "timeout");
            }
            OutputStream output = invocation.getArgument(1);
            output.write(new byte[]{4, 0});
            return new ShopAiKeyGeminiTtsClient.StreamMetrics(2, 10, 20,
                    ShopAiKeyGeminiTtsClient.CONTENT_TYPE);
        }).when(ttsClient).streamSpeech(anyString(), any(OutputStream.class));

        assertThat(service.prefetchAndAwait("Câu hỏi có thể thử lại", 1_000)).isNull();
        nanoTime.addAndGet(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(
                properties.getTtsPrefetchCooldownMs() + 1L));

        assertThat(service.prefetchAndAwait("Câu hỏi có thể thử lại", 1_000))
                .containsExactly(4, 0);
        verify(ttsClient, times(2)).streamSpeech(anyString(), any(OutputStream.class));
    }
}
