package com.sjp.recruitment.service.ai;

import jakarta.annotation.PreDestroy;
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

@Service
public class AiInterviewSpeechPrefetchService {

    private final ShopAiKeyGeminiTtsClient ttsClient;
    private final AiInterviewSpeechCache cache;
    private final ExecutorService executor;
    private final Map<String, CompletableFuture<Void>> inFlight = new java.util.concurrent.ConcurrentHashMap<>();

    public AiInterviewSpeechPrefetchService(
            ShopAiKeyGeminiTtsClient ttsClient,
            AiInterviewSpeechCache cache
    ) {
        this.ttsClient = ttsClient;
        this.cache = cache;
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

    public byte[] awaitReady(String cacheKey, long waitMillis) {
        CompletableFuture<Void> task = inFlight.get(cacheKey);
        if (task == null) {
            return null;
        }
        try {
            task.get(Math.max(1, waitMillis), TimeUnit.MILLISECONDS);
            return cache.get(cacheKey);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException exception) {
            return null;
        }
    }

    private void prefetchOne(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        String cacheKey = cache.key(normalized);
        if (cache.get(cacheKey) != null) {
            return;
        }
        inFlight.computeIfAbsent(cacheKey, ignored -> CompletableFuture.runAsync(() -> {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ttsClient.streamSpeech(normalized, output);
            cache.put(cacheKey, output.toByteArray());
        }, executor).whenComplete((unused, error) -> inFlight.remove(cacheKey)));
    }

    @PreDestroy
    public void shutdown() {
        inFlight.values().forEach(task -> task.cancel(true));
        executor.shutdownNow();
    }
}
