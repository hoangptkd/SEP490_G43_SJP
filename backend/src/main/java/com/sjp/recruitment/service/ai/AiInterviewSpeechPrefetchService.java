package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiInterviewProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

@Service
@Slf4j
public class AiInterviewSpeechPrefetchService {

    private final ShopAiKeyGeminiTtsClient ttsClient;
    private final AiInterviewSpeechCache cache;
    private final AiInterviewProperties properties;
    private final LongSupplier nanoTime;
    private final ExecutorService executor;
    private final Map<String, CompletableFuture<Void>> inFlight = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicLong cooldownUntilNanos = new AtomicLong();

    @Autowired
    public AiInterviewSpeechPrefetchService(
            ShopAiKeyGeminiTtsClient ttsClient,
            AiInterviewSpeechCache cache,
            AiInterviewProperties properties
    ) {
        this(ttsClient, cache, properties, System::nanoTime);
    }

    AiInterviewSpeechPrefetchService(
            ShopAiKeyGeminiTtsClient ttsClient,
            AiInterviewSpeechCache cache,
            AiInterviewProperties properties,
            LongSupplier nanoTime
    ) {
        this.ttsClient = ttsClient;
        this.cache = cache;
        this.properties = properties;
        this.nanoTime = nanoTime;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "gemini-tts-prefetch");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void prefetch(List<String> questionTexts) {
        if (!ttsClient.isConfigured() || questionTexts == null) {
            return;
        }
        questionTexts.stream()
                .filter(text -> text != null && !text.isBlank())
                .forEach(this::prefetchOne);
    }

    public byte[] awaitReady(String speechText, long waitMillis) {
        String normalized = AiInterviewSpeechCache.normalizeSpeech(speechText);
        byte[] cached = cache.getSpeech(normalized);
        if (cached != null) {
            return cached;
        }
        String cacheKey = cache.key(normalized);
        CompletableFuture<Void> task = inFlight.get(cacheKey);
        if (task == null) {
            // The task may have completed and removed itself between the first cache read
            // and this lookup. Read once more before treating the audio as unavailable.
            return cache.getSpeech(normalized);
        }
        try {
            task.get(Math.max(1, waitMillis), TimeUnit.MILLISECONDS);
            return cache.getSpeech(normalized);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException exception) {
            inFlight.remove(cacheKey, task);
            return null;
        } catch (TimeoutException exception) {
            return null;
        }
    }

    public byte[] prefetchAndAwait(String questionText, long waitMillis) {
        if (!ttsClient.isConfigured() || questionText == null || questionText.isBlank()) {
            return null;
        }
        String normalized = AiInterviewSpeechCache.normalizeSpeech(questionText);
        prefetchOne(normalized);
        return awaitReady(normalized, waitMillis);
    }

    public byte[] prefetchSegmentsAndAwait(String speechText, long waitMillis) {
        if (!ttsClient.isConfigured()) return null;
        String normalized = AiInterviewSpeechCache.normalizeSpeech(speechText);
        if (normalized.isBlank()) return null;
        List<String> segments = normalized.lines().filter(line -> !line.isBlank()).toList();
        segments.forEach(this::prefetchOne);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(1, waitMillis));
        for (String segment : segments) {
            long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remaining <= 0 || awaitReady(segment, remaining) == null) return null;
        }
        return cache.getSpeech(normalized);
    }

    private void prefetchOne(String text) {
        String normalized = AiInterviewSpeechCache.normalizeSpeech(text);
        if (normalized.isBlank()) return;
        String cacheKey = cache.key(normalized);
        if (cache.getSpeech(normalized) != null) {
            return;
        }
        if (isCoolingDown()) {
            log.debug("Gemini TTS prefetch skipped during provider cooldown: textLength={}",
                    normalized.length());
            return;
        }
        CompletableFuture<Void> task = inFlight.computeIfAbsent(cacheKey,
                ignored -> CompletableFuture.runAsync(() -> {
                    if (isCoolingDown()) {
                        log.debug("Queued Gemini TTS prefetch skipped during provider cooldown: textLength={}",
                                normalized.length());
                        return;
                    }
                    try {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        ttsClient.streamSpeech(normalized, output);
                        cache.put(cacheKey, output.toByteArray());
                    } catch (RuntimeException exception) {
                        openCooldown();
                        throw exception;
                    }
                }, executor));
        task.whenComplete((unused, error) -> {
            if (inFlight.remove(cacheKey, task) && error != null) {
                log.warn("Gemini TTS prefetch failed; provider cooldown opened: "
                                + "cooldownMs={}, textLength={}, detail={}",
                        properties.getTtsPrefetchCooldownMs(), normalized.length(), safeDetail(error));
            }
        });
    }

    private boolean isCoolingDown() {
        return nanoTime.getAsLong() < cooldownUntilNanos.get();
    }

    private void openCooldown() {
        long duration = TimeUnit.MILLISECONDS.toNanos(
                Math.max(1, properties.getTtsPrefetchCooldownMs()));
        long until = nanoTime.getAsLong() + duration;
        cooldownUntilNanos.accumulateAndGet(until, Math::max);
    }

    private String safeDetail(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String detail = current.getMessage();
        if (detail == null || detail.isBlank()) detail = current.getClass().getSimpleName();
        String normalized = detail.replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(240, normalized.length()));
    }

    @PreDestroy
    public void shutdown() {
        inFlight.values().forEach(task -> task.cancel(true));
        executor.shutdownNow();
    }
}
