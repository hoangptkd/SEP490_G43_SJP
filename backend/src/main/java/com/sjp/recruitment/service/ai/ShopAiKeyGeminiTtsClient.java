package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ShopAiKeyGeminiTtsClient {

    public static final String CONTENT_TYPE = "audio/L16;rate=24000;channels=1";
    private static final String EXPECTED_MIME_PREFIX = "audio/l16";
    private static final int MAX_ERROR_DETAIL_BYTES = 1_000;

    private final AiInterviewProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ScheduledExecutorService timeoutExecutor;

    @Autowired
    public ShopAiKeyGeminiTtsClient(AiInterviewProperties properties, ObjectMapper objectMapper) {
        this(
                properties,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(Math.max(1, properties.getTtsConnectTimeoutMs())))
                        .version(HttpClient.Version.HTTP_2)
                        .build(),
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "gemini-tts-stream-timeout");
                    thread.setDaemon(true);
                    return thread;
                })
        );
    }

    ShopAiKeyGeminiTtsClient(
            AiInterviewProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            ScheduledExecutorService timeoutExecutor
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.timeoutExecutor = timeoutExecutor;
    }

    public boolean isConfigured() {
        return properties.isVoiceConfigured()
                && "shopaikey_gemini_stream".equalsIgnoreCase(properties.getVoiceProvider());
    }

    public String contentType() {
        return CONTENT_TYPE;
    }

    public StreamMetrics streamSpeech(String text, OutputStream outputStream) {
        StreamMetrics metrics = streamSpeech(text, (pcm, mimeType) -> {
            outputStream.write(pcm);
            outputStream.flush();
        });
        try {
            outputStream.flush();
        } catch (IOException exception) {
            throw new AiProviderException("GEMINI_TTS_CLIENT_DISCONNECTED",
                    "Trình duyệt đã ngắt kết nối khỏi luồng audio");
        }
        return metrics;
    }

    public StreamMetrics streamSpeech(String text, AudioChunkConsumer audioConsumer) {
        if (!StringUtils.hasText(text)) {
            throw new AiProviderException("TTS_INPUT_REQUIRED", "Nội dung đọc không được để trống");
        }
        if (!isConfigured()) {
            throw new AiProviderException("GEMINI_TTS_NOT_CONFIGURED",
                    "Chưa cấu hình đầy đủ ShopAIKey Gemini TTS");
        }

        long started = System.nanoTime();
        long firstAudioDeadline = started + TimeUnit.MILLISECONDS.toNanos(properties.getTtsFirstAudioTimeoutMs());
        int attempt = 0;
        while (true) {
            HttpResponse<InputStream> response = send(text.trim(), firstAudioDeadline);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                try {
                    return readAudioStream(response.body(), audioConsumer, started, firstAudioDeadline);
                } catch (AiProviderException exception) {
                    boolean retryableEmptyAudio = "GEMINI_TTS_EMPTY_AUDIO".equals(exception.getCode())
                            && attempt == 0
                            && System.nanoTime() < firstAudioDeadline;
                    if (retryableEmptyAudio) {
                        attempt++;
                        continue;
                    }
                    throw exception;
                }
            }

            String detail = readErrorDetail(response.body());
            boolean retryable = response.statusCode() >= 500
                    && attempt == 0
                    && elapsedMillis(started) < 1_000
                    && System.nanoTime() < firstAudioDeadline;
            if (retryable) {
                attempt++;
                continue;
            }
            throw providerHttpException(response.statusCode(), detail);
        }
    }

    private HttpResponse<InputStream> send(String text, long firstAudioDeadline) {
        long remainingNanos = firstAudioDeadline - System.nanoTime();
        if (remainingNanos <= 0) {
            throw firstAudioTimeout();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint())
                    .header("Authorization", "Bearer " + properties.getShopaikeyApiKey().trim())
                    .header("Accept", "text/event-stream")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(text), StandardCharsets.UTF_8))
                    .build();
            CompletableFuture<HttpResponse<InputStream>> requestFuture =
                    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
            try {
                return requestFuture.get(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (TimeoutException exception) {
                requestFuture.cancel(true);
                throw exception;
            }
        } catch (TimeoutException exception) {
            throw firstAudioTimeout();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("GEMINI_TTS_INTERRUPTED", "Tác vụ Gemini TTS đã bị gián đoạn");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            String detail = cause == null ? exception.getClass().getSimpleName() : cause.getClass().getSimpleName();
            throw new AiProviderException("GEMINI_TTS_CONNECTION_FAILED",
                    "Không thể kết nối ShopAIKey Gemini TTS: " + detail);
        }
    }

    private StreamMetrics readAudioStream(
            InputStream responseStream,
            AudioChunkConsumer audioConsumer,
            long started,
            long firstAudioDeadline
    ) {
        long remainingNanos = firstAudioDeadline - System.nanoTime();
        if (remainingNanos <= 0) {
            closeQuietly(responseStream);
            throw firstAudioTimeout();
        }

        AtomicBoolean receivedAudio = new AtomicBoolean(false);
        AtomicBoolean timedOut = new AtomicBoolean(false);
        StreamWatchdog watchdog = new StreamWatchdog(responseStream, timedOut);
        watchdog.armNanos(remainingNanos);

        long audioBytes = 0;
        long firstAudioMillis = -1;
        String mimeType = null;
        boolean completed = false;
        String terminalReason = null;
        StringBuilder eventData = new StringBuilder();
        try (InputStream input = responseStream;
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    EventAudio eventAudio = processEvent(eventData, audioConsumer);
                    eventData.setLength(0);
                    if (eventAudio.bytes() > 0) {
                        if (receivedAudio.compareAndSet(false, true)) {
                            firstAudioMillis = elapsedMillis(started);
                        }
                        audioBytes += eventAudio.bytes();
                        mimeType = eventAudio.mimeType();
                        watchdog.armMillis(properties.getTtsIdleTimeoutMs());
                    }
                    if (eventAudio.terminal()) {
                        completed = true;
                        terminalReason = eventAudio.terminalReason();
                        break;
                    }
                    continue;
                }
                if (line.startsWith("data:")) {
                    if (!eventData.isEmpty()) {
                        eventData.append('\n');
                    }
                    eventData.append(line.substring(5).stripLeading());
                }
            }
            if (!completed && !eventData.isEmpty()) {
                EventAudio eventAudio = processEvent(eventData, audioConsumer);
                if (eventAudio.bytes() > 0) {
                    if (receivedAudio.compareAndSet(false, true)) {
                        firstAudioMillis = elapsedMillis(started);
                    }
                    audioBytes += eventAudio.bytes();
                    mimeType = eventAudio.mimeType();
                }
                completed = eventAudio.terminal();
                terminalReason = eventAudio.terminalReason();
            }
        } catch (IOException exception) {
            if (timedOut.get()) {
                throw receivedAudio.get() ? idleTimeout() : firstAudioTimeout();
            }
            throw new AiProviderException("GEMINI_TTS_STREAM_FAILED",
                    "Luồng audio Gemini TTS bị gián đoạn");
        } finally {
            watchdog.cancel();
        }

        if (timedOut.get()) {
            throw receivedAudio.get() ? idleTimeout() : firstAudioTimeout();
        }
        if (audioBytes <= 0) {
            String reasonDetail = StringUtils.hasText(terminalReason)
                    ? " (finishReason=" + safeDetail(terminalReason) + ")"
                    : "";
            throw new AiProviderException("GEMINI_TTS_EMPTY_AUDIO",
                    "Gemini TTS không trả về dữ liệu audio" + reasonDetail);
        }
        if (!completed) {
            throw new AiProviderException("GEMINI_TTS_INCOMPLETE_STREAM",
                    "Gemini TTS kết thúc mà không có tín hiệu hoàn tất");
        }
        return new StreamMetrics(audioBytes, firstAudioMillis, elapsedMillis(started), mimeType);
    }

    private EventAudio processEvent(StringBuilder eventData, AudioChunkConsumer audioConsumer) throws IOException {
        if (eventData.isEmpty()) {
            return EventAudio.empty();
        }
        if ("[DONE]".contentEquals(eventData)) {
            return EventAudio.terminalEvent();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(eventData.toString());
        } catch (JsonProcessingException exception) {
            throw new AiProviderException("GEMINI_TTS_INVALID_SSE", "ShopAIKey trả về SSE không hợp lệ");
        }
        if (root.has("error")) {
            String message = root.path("error").path("message").asText("ShopAIKey trả về lỗi không xác định");
            throw new AiProviderException("GEMINI_TTS_PROVIDER_ERROR", safeDetail(message));
        }

        JsonNode promptFeedback = root.has("promptFeedback")
                ? root.path("promptFeedback") : root.path("prompt_feedback");
        String blockReason = promptFeedback.path("blockReason").asText(
                promptFeedback.path("block_reason").asText("")).trim();
        if (StringUtils.hasText(blockReason)
                && !"BLOCK_REASON_UNSPECIFIED".equalsIgnoreCase(blockReason)) {
            throw providerBlocked("blockReason=" + blockReason);
        }

        long bytes = 0;
        String mimeType = null;
        boolean terminal = false;
        String terminalReason = null;
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray()) {
            return EventAudio.empty();
        }
        for (JsonNode candidate : candidates) {
            String finishReason = candidate.path("finishReason").asText(
                    candidate.path("finish_reason").asText("")).trim();
            if (StringUtils.hasText(finishReason)
                    && !"FINISH_REASON_UNSPECIFIED".equalsIgnoreCase(finishReason)) {
                if (isBlockedFinishReason(finishReason)) {
                    throw providerBlocked("finishReason=" + finishReason);
                }
                terminal = true;
                terminalReason = finishReason;
            }
            JsonNode parts = candidate.path("content").path("parts");
            if (!parts.isArray()) {
                continue;
            }
            for (JsonNode part : parts) {
                JsonNode inlineData = part.has("inlineData") ? part.path("inlineData") : part.path("inline_data");
                String currentMimeType = inlineData.path("mimeType").asText(
                        inlineData.path("mime_type").asText("")).trim();
                String data = inlineData.path("data").asText("").trim();
                if (data.isEmpty()) {
                    continue;
                }
                if (!currentMimeType.toLowerCase().startsWith(EXPECTED_MIME_PREFIX)) {
                    throw new AiProviderException("GEMINI_TTS_UNSUPPORTED_AUDIO",
                            "Gemini TTS trả về định dạng audio không được hỗ trợ: " + safeDetail(currentMimeType));
                }
                byte[] pcm;
                try {
                    pcm = Base64.getDecoder().decode(data);
                } catch (IllegalArgumentException exception) {
                    throw new AiProviderException("GEMINI_TTS_INVALID_AUDIO", "Gemini TTS trả về audio base64 không hợp lệ");
                }
                if ((pcm.length & 1) != 0) {
                    throw new AiProviderException("GEMINI_TTS_INVALID_AUDIO", "Gemini TTS trả về PCM không hợp lệ");
                }
                audioConsumer.accept(pcm, currentMimeType);
                bytes += pcm.length;
                mimeType = currentMimeType;
            }
        }
        return new EventAudio(bytes, mimeType, terminal, terminalReason);
    }

    private AiProviderException providerBlocked(String detail) {
        return new AiProviderException("GEMINI_TTS_PROVIDER_BLOCKED",
                "Gemini TTS không tạo audio do bộ lọc nhà cung cấp (" + safeDetail(detail) + ")");
    }

    private boolean isBlockedFinishReason(String finishReason) {
        return switch (finishReason.toUpperCase()) {
            case "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY" -> true;
            default -> false;
        };
    }

    private String requestBody(String text) {
        String instruction = properties.getTtsVoiceInstruction().trim();
        String prompt = instruction + "\n\nTranscript:\n" + text;
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseModalities", List.of("AUDIO"),
                        "speechConfig", Map.of(
                                "voiceConfig", Map.of(
                                        "prebuiltVoiceConfig", Map.of("voiceName", properties.getTtsVoice().trim())
                                )
                        )
                )
        );
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new AiProviderException("GEMINI_TTS_REQUEST_FAILED", "Không thể tạo request Gemini TTS");
        }
    }

    private URI endpoint() {
        String baseUrl = trimTrailingSlash(properties.getTtsBaseUrl());
        String model = properties.getTtsModel().trim();
        if (!model.matches("[A-Za-z0-9._-]+")) {
            throw new AiProviderException("GEMINI_TTS_MODEL_INVALID", "Tên model Gemini TTS không hợp lệ");
        }
        return URI.create(baseUrl + "/v1beta/models/" + model + ":streamGenerateContent");
    }

    private AiProviderException providerHttpException(int status, String detail) {
        if (status == 401 || status == 403) {
            return new AiProviderException("GEMINI_TTS_AUTH_FAILED", "ShopAIKey từ chối API key TTS");
        }
        if (status == 429) {
            return new AiProviderException("GEMINI_TTS_RATE_LIMITED", "ShopAIKey TTS đang giới hạn tần suất");
        }
        return new AiProviderException("GEMINI_TTS_HTTP_ERROR",
                "ShopAIKey Gemini TTS phản hồi HTTP " + status + ": " + safeDetail(detail));
    }

    private AiProviderException firstAudioTimeout() {
        return new AiProviderException("GEMINI_TTS_FIRST_AUDIO_TIMEOUT",
                "Gemini TTS không trả audio trong " + properties.getTtsFirstAudioTimeoutMs() + " ms");
    }

    private AiProviderException idleTimeout() {
        return new AiProviderException("GEMINI_TTS_IDLE_TIMEOUT",
                "Luồng Gemini TTS ngừng phản hồi quá " + properties.getTtsIdleTimeoutMs() + " ms");
    }

    private String readErrorDetail(InputStream inputStream) {
        try (InputStream input = inputStream) {
            return new String(input.readNBytes(MAX_ERROR_DETAIL_BYTES), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "không đọc được nội dung lỗi";
        }
    }

    private static String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "https://api.shopaikey.com";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static String safeDetail(String detail) {
        String value = StringUtils.hasText(detail) ? detail.replaceAll("\\s+", " ").trim() : "không có chi tiết";
        return value.substring(0, Math.min(value.length(), 500));
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // Closing the provider stream is best effort during timeout cleanup.
        }
    }

    @PreDestroy
    public void shutdown() {
        timeoutExecutor.shutdownNow();
    }

    public record StreamMetrics(long pcmBytes, long firstAudioMillis, long totalMillis, String mimeType) {
    }

    @FunctionalInterface
    public interface AudioChunkConsumer {
        void accept(byte[] pcm, String mimeType) throws IOException;
    }

    private record EventAudio(long bytes, String mimeType, boolean terminal, String terminalReason) {
        private static EventAudio empty() {
            return new EventAudio(0, null, false, null);
        }

        private static EventAudio terminalEvent() {
            return new EventAudio(0, null, true, "DONE");
        }
    }

    private final class StreamWatchdog {
        private final InputStream inputStream;
        private final AtomicBoolean timedOut;
        private ScheduledFuture<?> task;

        private StreamWatchdog(InputStream inputStream, AtomicBoolean timedOut) {
            this.inputStream = inputStream;
            this.timedOut = timedOut;
        }

        private synchronized void armMillis(long timeoutMillis) {
            armNanos(TimeUnit.MILLISECONDS.toNanos(Math.max(1, timeoutMillis)));
        }

        private synchronized void armNanos(long timeoutNanos) {
            cancel();
            task = timeoutExecutor.schedule(() -> {
                timedOut.set(true);
                closeQuietly(inputStream);
            }, Math.max(1, timeoutNanos), TimeUnit.NANOSECONDS);
        }

        private synchronized void cancel() {
            if (task != null) {
                task.cancel(false);
                task = null;
            }
        }
    }
}
