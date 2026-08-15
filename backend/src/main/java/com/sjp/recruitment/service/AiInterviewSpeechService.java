package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.AiInterviewSpeechTicketResponse;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.service.ai.AiProviderException;
import com.sjp.recruitment.service.ai.AiInterviewRateLimiter;
import com.sjp.recruitment.service.ai.AiInterviewSpeechCache;
import com.sjp.recruitment.service.ai.AiInterviewSpeechPrefetchService;
import com.sjp.recruitment.service.ai.ShopAiKeyGeminiTtsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiInterviewSpeechService {

    private static final long TICKET_TTL_SECONDS = 120;
    private static final int MAX_INPUT_LENGTH = 2_000;
    private static final int CACHE_STREAM_CHUNK_BYTES = 24_000;
    private static final String SSE_CONTENT_TYPE = "text/event-stream;charset=UTF-8";
    private static final String STREAM_ERROR_MESSAGE =
            "Không thể phát trọn vẹn giọng đọc. Hãy đọc câu hỏi trên màn hình.";

    private final CandidateService candidateService;
    private final AiInterviewRateLimiter rateLimiter;
    private final ShopAiKeyGeminiTtsClient geminiTtsClient;
    private final AiInterviewSpeechCache speechCache;
    private final AiInterviewSpeechPrefetchService speechPrefetchService;
    private final AiInterviewProperties properties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, SpeechTicket> tickets = new ConcurrentHashMap<>();

    public AiInterviewSpeechTicketResponse createTicket(String input) {
        if (!properties.isCoreConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_INTERVIEW_DISABLED", "AI Interview chưa được cấu hình.");
        }
        if (!"shopaikey_gemini_stream".equalsIgnoreCase(properties.getVoiceProvider())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TTS_PROVIDER_DISABLED",
                    "Chưa bật ShopAIKey Gemini streaming TTS.");
        }
        if (!geminiTtsClient.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_TTS_NOT_CONFIGURED",
                    "Chưa cấu hình đầy đủ ShopAIKey Gemini TTS.");
        }
        String normalizedInput = input == null ? "" : input.replaceAll("\\s+", " ").trim();
        if (normalizedInput.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TTS_INPUT_REQUIRED", "Nội dung đọc không được để trống.");
        }
        if (normalizedInput.length() > MAX_INPUT_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TTS_INPUT_TOO_LONG", "Nội dung đọc vượt quá giới hạn cho phép.");
        }
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "speech");
        cleanupExpiredTickets();

        String token = newToken();
        long expiresAt = Instant.now().plusSeconds(TICKET_TTL_SECONDS).toEpochMilli();
        tickets.put(token, new SpeechTicket(normalizedInput, SSE_CONTENT_TYPE, expiresAt));
        return new AiInterviewSpeechTicketResponse(
                "/api/candidate/ai-interviews/speech/" + token,
                SSE_CONTENT_TYPE,
                expiresAt
        );
    }

    public String contentTypeForTicket(String token) {
        return requireTicket(token).contentType();
    }

    public void streamSpeech(String token, OutputStream outputStream) {
        SseAudioWriter writer = new SseAudioWriter(outputStream, objectMapper);
        long started = System.nanoTime();
        boolean cacheHit = false;
        try {
            SpeechTicket ticket = requireTicket(token);
            String cacheKey = speechCache.key(ticket.input());
            byte[] cached = speechCache.get(cacheKey);
            if (cached == null) {
                cached = speechPrefetchService.awaitReady(cacheKey, 150);
            }
            cacheHit = cached != null;
            if (cached != null) {
                if ((cached.length & 1) != 0) {
                    throw new AiProviderException("GEMINI_TTS_INVALID_CACHE", "PCM cache không hợp lệ");
                }
                for (int offset = 0; offset < cached.length; offset += CACHE_STREAM_CHUNK_BYTES) {
                    int length = Math.min(CACHE_STREAM_CHUNK_BYTES, cached.length - offset);
                    byte[] chunk = java.util.Arrays.copyOfRange(cached, offset, offset + length);
                    writer.audio(chunk, geminiTtsClient.contentType());
                }
                writer.done();
                return;
            }
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            geminiTtsClient.streamSpeech(ticket.input(), (pcm, mimeType) -> {
                captured.writeBytes(pcm);
                try {
                    writer.audio(pcm, mimeType);
                } catch (IOException exception) {
                    throw new AiProviderException("GEMINI_TTS_CLIENT_DISCONNECTED",
                            "Trình duyệt đã ngắt kết nối khỏi luồng audio");
                }
            });
            speechCache.put(cacheKey, captured.toByteArray());
            writer.done();
        } catch (ApiException exception) {
            log.warn("Gemini TTS ticket failed during async stream: code={}", exception.getCode());
            writer.tryError(exception.getCode(), STREAM_ERROR_MESSAGE);
        } catch (AiProviderException exception) {
            if ("GEMINI_TTS_CLIENT_DISCONNECTED".equals(exception.getCode())) {
                log.debug("Gemini TTS downstream disconnected: cacheHit={}, chunks={}, bytes={}",
                        cacheHit, writer.chunks(), writer.audioBytes());
                return;
            }
            log.warn("Gemini TTS stream failed: code={}, detail={}, cacheHit={}, chunks={}, bytes={}, elapsedMs={}",
                    exception.getCode(), safeFailureDetail(exception.getMessage()), cacheHit,
                    writer.chunks(), writer.audioBytes(), elapsedMillis(started));
            writer.tryError(exception.getCode(), STREAM_ERROR_MESSAGE);
        } catch (IOException exception) {
            log.debug("Gemini TTS SSE client disconnected: cacheHit={}, chunks={}, bytes={}",
                    cacheHit, writer.chunks(), writer.audioBytes());
        } catch (RuntimeException exception) {
            log.error("Unexpected Gemini TTS stream failure: cacheHit={}, chunks={}, bytes={}",
                    cacheHit, writer.chunks(), writer.audioBytes(), exception);
            writer.tryError("GEMINI_TTS_STREAM_FAILED", STREAM_ERROR_MESSAGE);
        }
    }

    private SpeechTicket requireTicket(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TTS_TICKET_NOT_FOUND", "Không tìm thấy phiên audio.");
        }
        SpeechTicket ticket = tickets.get(token);
        if (ticket == null || ticket.expiresAt() < Instant.now().toEpochMilli()) {
            tickets.remove(token);
            throw new ApiException(HttpStatus.NOT_FOUND, "TTS_TICKET_EXPIRED", "Phiên audio đã hết hạn.");
        }
        return ticket;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void cleanupExpiredTickets() {
        long now = Instant.now().toEpochMilli();
        Iterator<Map.Entry<String, SpeechTicket>> iterator = tickets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt() < now) {
                iterator.remove();
            }
        }
    }

    private record SpeechTicket(String input, String contentType, long expiresAt) {
    }

    private static long elapsedMillis(long started) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static String safeFailureDetail(String detail) {
        String normalized = detail == null ? "không có chi tiết" : detail.replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(normalized.length(), 300));
    }

    private static final class SseAudioWriter {
        private final OutputStream outputStream;
        private final ObjectMapper objectMapper;
        private int sequence;
        private long audioBytes;
        private boolean terminal;

        private SseAudioWriter(OutputStream outputStream, ObjectMapper objectMapper) {
            this.outputStream = outputStream;
            this.objectMapper = objectMapper;
        }

        private void audio(byte[] pcm, String mimeType) throws IOException {
            if (terminal) {
                return;
            }
            writeEvent("audio", new AudioEvent(
                    sequence++,
                    mimeType,
                    Base64.getEncoder().encodeToString(pcm)
            ));
            audioBytes += pcm.length;
        }

        private void done() throws IOException {
            if (terminal) {
                return;
            }
            terminal = true;
            writeEvent("done", new DoneEvent(audioBytes, sequence));
        }

        private void tryError(String code, String message) {
            if (terminal) {
                return;
            }
            terminal = true;
            try {
                writeEvent("error", new ErrorEvent(code, message));
            } catch (IOException ignored) {
                // The browser disconnected, so no downstream error event can be delivered.
            }
        }

        private void writeEvent(String event, Object data) throws IOException {
            String payload = "event: " + event + "\n"
                    + "data: " + objectMapper.writeValueAsString(data) + "\n\n";
            outputStream.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            outputStream.flush();
        }

        private int chunks() {
            return sequence;
        }

        private long audioBytes() {
            return audioBytes;
        }
    }

    private record AudioEvent(int sequence, String mimeType, String data) {
    }

    private record DoneEvent(long audioBytes, int chunks) {
    }

    private record ErrorEvent(String code, String message) {
    }
}
