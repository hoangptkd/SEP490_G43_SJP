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
                .forEach(text -> prefetchOne(text, null));
    }

    public void prefetchLabeled(List<PrefetchRequest> requests) {
        if (!ttsClient.isConfigured() || requests == null) {
            return;
        }
        requests.stream()
                .filter(request -> request != null
                        && request.text() != null
                        && !request.text().isBlank())
                .forEach(request -> prefetchOne(request.text(), request.label()));
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
        prefetchOne(normalized, null);
        return awaitReady(normalized, waitMillis);
    }

    public byte[] prefetchSegmentsAndAwait(String speechText, long waitMillis) {
        return prefetchSegmentsAndAwait(speechText, waitMillis, List.of());
    }

    public byte[] prefetchSegmentsAndAwait(
            String speechText,
            long waitMillis,
            List<String> segmentLabels
    ) {
        if (!ttsClient.isConfigured()) return null;
        String normalized = AiInterviewSpeechCache.normalizeSpeech(speechText);
        if (normalized.isBlank()) return null;
        List<String> segments = normalized.lines().filter(line -> !line.isBlank()).toList();
        for (int index = 0; index < segments.size(); index++) {
            prefetchOne(segments.get(index), segmentLabel(segmentLabels, index));
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(1, waitMillis));
        for (String segment : segments) {
            long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remaining <= 0 || awaitReady(segment, remaining) == null) return null;
        }
        return cache.getSpeech(normalized);
    }

    private void prefetchOne(String text, String label) {
        String normalized = AiInterviewSpeechCache.normalizeSpeech(text);
        if (normalized.isBlank()) return;
        String cacheKey = cache.key(normalized);
        if (cache.getSpeech(normalized) != null) {
            if (hasLabel(label)) {
                log.info("AI interview TTS prefetch cache hit: label={}", label);
            }
            return;
        }
        if (isCoolingDown()) {
            log.info("AI interview TTS prefetch skipped during provider cooldown: label={}, "
                            + "cooldownMs={}, textLength={}",
                    safeLabel(label), properties.getTtsPrefetchCooldownMs(), normalized.length());
            return;
        }
        long queuedAtNanos = nanoTime.getAsLong();
        CompletableFuture<Void> task = inFlight.computeIfAbsent(cacheKey,
                ignored -> CompletableFuture.runAsync(() -> {
                    if (isCoolingDown()) {
                        log.info("Queued AI interview TTS prefetch skipped during provider cooldown: "
                                        + "label={}, cooldownMs={}, queueWaitMs={}, textLength={}",
                                safeLabel(label), properties.getTtsPrefetchCooldownMs(),
                                elapsedMillis(queuedAtNanos), normalized.length());
                        return;
                    }
                    long startedAtNanos = nanoTime.getAsLong();
                    if (hasLabel(label)) {
                        log.info("AI interview TTS prefetch started: label={}, queueWaitMs={}, "
                                        + "connectTimeoutMs={}, firstAudioTimeoutMs={}, idleTimeoutMs={}",
                                label, elapsedMillis(queuedAtNanos),
                                properties.getTtsConnectTimeoutMs(),
                                properties.getTtsFirstAudioTimeoutMs(),
                                properties.getTtsIdleTimeoutMs());
                    }
                    try {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        ttsClient.streamSpeech(normalized, output);
                        byte[] audio = output.toByteArray();
                        cache.put(cacheKey, audio);
                        if (hasLabel(label)) {
                            log.info("AI interview TTS prefetch succeeded: label={}, elapsedMs={}, audioBytes={}",
                                    label, elapsedMillis(startedAtNanos), audio.length);
                        }
                    } catch (RuntimeException exception) {
                        if (shouldOpenProviderCooldown(exception)) {
                            openCooldown();
                        }
                        throw exception;
                    }
                }, executor));
        task.whenComplete((unused, error) -> {
            if (inFlight.remove(cacheKey, task) && error != null) {
                if (shouldOpenProviderCooldown(error)) {
                    log.warn("AI interview TTS prefetch failed; provider cooldown opened: "
                                    + "label={}, cooldownMs={}, textLength={}, detail={}",
                            safeLabel(label), properties.getTtsPrefetchCooldownMs(),
                            normalized.length(), safeDetail(error));
                } else {
                    log.warn("AI interview TTS prefetch failed for one segment; queue continues: "
                                    + "label={}, textLength={}, detail={}",
                            safeLabel(label), normalized.length(), safeDetail(error));
                }
            }
        });
    }

    private String segmentLabel(List<String> labels, int index) {
        if (labels == null || labels.isEmpty()) return null;
        return labels.get(Math.min(index, labels.size() - 1));
    }

    private boolean shouldOpenProviderCooldown(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof AiProviderException providerException) {
                return switch (providerException.getCode()) {
                    case "GEMINI_TTS_FIRST_AUDIO_TIMEOUT",
                         "GEMINI_TTS_IDLE_TIMEOUT",
                         "GEMINI_TTS_RATE_LIMITED",
                         "GEMINI_TTS_HTTP_5XX" -> true;
                    default -> false;
                };
            }
            if (current.getCause() == null || current.getCause() == current) break;
            current = current.getCause();
        }
        return false;
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0, nanoTime.getAsLong() - startedAtNanos));
    }

    private boolean hasLabel(String label) {
        return label != null && !label.isBlank();
    }

    private String safeLabel(String label) {
        return hasLabel(label) ? label : "unlabeled";
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

    public record PrefetchRequest(String text, String label) {
    }
}
