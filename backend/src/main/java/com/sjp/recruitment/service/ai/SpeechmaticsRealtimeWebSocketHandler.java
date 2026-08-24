package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.service.SpeechmaticsRealtimeTicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class SpeechmaticsRealtimeWebSocketHandler extends AbstractWebSocketHandler {
    private static final CloseStatus POLICY_VIOLATION = new CloseStatus(1008, "Invalid transcription session");
    private static final CloseStatus UPSTREAM_FAILURE = new CloseStatus(1011, "Transcription provider unavailable");
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 256 * 1024;

    private final AiInterviewProperties properties;
    private final SpeechmaticsRealtimeTicketService ticketService;
    private final ObjectMapper objectMapper;
    private final StandardWebSocketClient upstreamClient = new StandardWebSocketClient();
    private final Map<String, ProxyState> states = new ConcurrentHashMap<>();
    private final AtomicInteger activeSessions = new AtomicInteger();

    public SpeechmaticsRealtimeWebSocketHandler(
            AiInterviewProperties properties,
            SpeechmaticsRealtimeTicketService ticketService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.ticketService = ticketService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (activeSessions.get() >= Math.max(1, properties.getSpeechmaticsMaxConcurrentSessions())) {
            session.close(new CloseStatus(1013, "Transcription capacity reached"));
            return;
        }
        String ticket = extractTicket(session.getUri());
        try {
            ticketService.consume(ticket);
        } catch (ApiException exception) {
            session.close(POLICY_VIOLATION);
            return;
        }

        WebSocketSession safeBrowser = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        ProxyState state = new ProxyState(safeBrowser, maxAudioBytes());
        states.put(session.getId(), state);
        state.counted.set(true);
        activeSessions.incrementAndGet();
        connectUpstream(session.getId(), state);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        ProxyState state = states.get(session.getId());
        if (state == null || !state.recognitionStarted.get() || state.ended.get()) {
            closeBrowser(state, POLICY_VIOLATION);
            return;
        }
        long bytes = state.audioBytes.addAndGet(message.getPayloadLength());
        if (bytes > state.maxAudioBytes) {
            closeBoth(state, POLICY_VIOLATION);
            return;
        }
        WebSocketSession upstream = state.upstream;
        if (upstream == null || !upstream.isOpen()) {
            closeBoth(state, UPSTREAM_FAILURE);
            return;
        }
        if (!send(upstream, new BinaryMessage(message.getPayload().slice(), true))) {
            closeBoth(state, UPSTREAM_FAILURE);
            return;
        }
        state.audioChunks.incrementAndGet();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ProxyState state = states.get(session.getId());
        if (state == null || state.ended.get() || message.getPayloadLength() > 1_024) {
            closeBrowser(state, POLICY_VIOLATION);
            return;
        }
        JsonNode payload;
        try {
            payload = objectMapper.readTree(message.getPayload());
        } catch (IOException exception) {
            closeBoth(state, POLICY_VIOLATION);
            return;
        }
        if (!"EndOfStream".equals(payload.path("message").asText())) {
            closeBoth(state, POLICY_VIOLATION);
            return;
        }
        if (!state.ended.compareAndSet(false, true)) return;
        WebSocketSession upstream = state.upstream;
        if (upstream == null || !upstream.isOpen()) {
            closeBoth(state, UPSTREAM_FAILURE);
            return;
        }
        int lastSeqNo = Math.max(0, state.audioChunks.get() - 1);
        if (!send(upstream, new TextMessage(objectMapper.writeValueAsString(Map.of(
                "message", "EndOfStream",
                "last_seq_no", lastSeqNo
        ))))) {
            closeBoth(state, UPSTREAM_FAILURE);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        closeBoth(states.get(session.getId()), UPSTREAM_FAILURE);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ProxyState state = states.get(session.getId());
        if (state == null) return;
        state.browserClosing.set(true);
        states.remove(session.getId(), state);
        closeUpstream(state, CloseStatus.NORMAL);
        releaseCapacity(state);
    }

    private void connectUpstream(String browserSessionId, ProxyState state) {
        String apiKey = properties.getSpeechmaticsApiKey();
        if (!StringUtils.hasText(apiKey)) {
            closeBoth(state, UPSTREAM_FAILURE);
            return;
        }
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
        WebSocketHandler upstreamHandler = new UpstreamHandler(browserSessionId, state);
        upstreamClient.execute(upstreamHandler, headers, URI.create(properties.getSpeechmaticsRealtimeUrl()))
                .orTimeout(Math.max(1_000, properties.getSpeechmaticsConnectTimeoutMs()), TimeUnit.MILLISECONDS)
                .exceptionally(exception -> {
                    if (states.get(browserSessionId) == state) {
                        log.warn("Speechmatics realtime connection failed for websocketSessionId={}", browserSessionId);
                        sendProviderError(state);
                        closeBoth(state, UPSTREAM_FAILURE);
                    }
                    return null;
                });
    }

    private TextMessage startRecognitionMessage() throws IOException {
        Map<String, Object> transcriptionConfig = new LinkedHashMap<>();
        transcriptionConfig.put("language", properties.getSpeechmaticsLanguage());
        transcriptionConfig.put("operating_point", properties.getSpeechmaticsOperatingPoint());
        transcriptionConfig.put("enable_partials", true);
        transcriptionConfig.put("max_delay", Math.max(0.7, Math.min(4.0,
                properties.getSpeechmaticsMaxDelaySeconds())));
        List<Map<String, String>> vocabulary = properties.getGladiaBaseVocabulary() == null
                ? List.of()
                : properties.getGladiaBaseVocabulary().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(100)
                .map(content -> Map.of("content", content))
                .toList();
        if (!vocabulary.isEmpty()) transcriptionConfig.put("additional_vocab", vocabulary);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("message", "StartRecognition");
        request.put("audio_format", Map.of(
                "type", "raw",
                "encoding", "pcm_s16le",
                "sample_rate", 16_000
        ));
        request.put("transcription_config", transcriptionConfig);
        return new TextMessage(objectMapper.writeValueAsString(request));
    }

    private long maxAudioBytes() {
        return Math.max(1, properties.getAudioMaxSeconds()) * 16_000L * 2L;
    }

    private String extractTicket(URI uri) {
        if (uri == null) return null;
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("ticket");
    }

    private void sendProviderError(ProxyState state) {
        if (state == null || state.browserClosing.get()) return;
        try {
            send(state.browser, new TextMessage("{\"message\":\"ProxyError\",\"type\":\"upstream_unavailable\"}"));
        } catch (IOException | IllegalStateException ignored) {
            // Best effort only: the browser may close between isOpen() and sendMessage().
        }
    }

    private boolean send(WebSocketSession session, WebSocketMessage<?> message) throws IOException {
        if (session == null || !session.isOpen()) return false;
        try {
            session.sendMessage(message);
            return true;
        } catch (IllegalStateException exception) {
            log.debug("WebSocket closed before queued message could be sent: sessionId={}, messageType={}",
                    session.getId(), message.getClass().getSimpleName());
            return false;
        }
    }

    private void closeBrowser(ProxyState state, CloseStatus status) {
        if (state == null) return;
        state.browserClosing.set(true);
        try {
            if (state.browser.isOpen()) state.browser.close(status);
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }

    private void closeUpstream(ProxyState state, CloseStatus status) {
        if (state == null) return;
        WebSocketSession upstream = state.upstream;
        try {
            if (upstream != null && upstream.isOpen()) upstream.close(status);
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }

    private void closeBoth(ProxyState state, CloseStatus status) {
        if (state == null) return;
        state.browserClosing.set(true);
        closeUpstream(state, status);
        closeBrowser(state, status);
    }

    private void releaseCapacity(ProxyState state) {
        if (state != null && state.counted.compareAndSet(true, false)) activeSessions.decrementAndGet();
    }

    private final class UpstreamHandler extends AbstractWebSocketHandler {
        private final String browserSessionId;
        private final ProxyState state;

        private UpstreamHandler(String browserSessionId, ProxyState state) {
            this.browserSessionId = browserSessionId;
            this.state = state;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            if (states.get(browserSessionId) != state || !state.browser.isOpen()) {
                session.close(CloseStatus.NORMAL);
                return;
            }
            state.upstream = new ConcurrentWebSocketSessionDecorator(
                    session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
            if (!send(state.upstream, startRecognitionMessage())) {
                closeBoth(state, UPSTREAM_FAILURE);
            }
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            JsonNode payload = objectMapper.readTree(message.getPayload());
            String type = payload.path("message").asText();
            if ("RecognitionStarted".equals(type)) state.recognitionStarted.set(true);
            if (state.browserClosing.get()
                    || !send(state.browser, new TextMessage(message.getPayload()))) {
                closeUpstream(state, CloseStatus.NORMAL);
                return;
            }
            if ("EndOfTranscript".equals(type)) {
                // The browser owns the final close after consuming this message. Keeping its side
                // open here avoids racing the final transcript delivery with a server-side close.
                closeUpstream(state, CloseStatus.NORMAL);
            } else if ("Error".equals(type)) {
                closeBoth(state, UPSTREAM_FAILURE);
            }
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            sendProviderError(state);
            closeBoth(state, UPSTREAM_FAILURE);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            state.upstream = null;
            if (states.get(browserSessionId) == state
                    && !state.browserClosing.get()
                    && state.browser.isOpen()
                    && !state.ended.get()) {
                sendProviderError(state);
                closeBrowser(state, UPSTREAM_FAILURE);
            }
        }
    }

    private static final class ProxyState {
        private final WebSocketSession browser;
        private final long maxAudioBytes;
        private final java.util.concurrent.atomic.AtomicLong audioBytes = new java.util.concurrent.atomic.AtomicLong();
        private final AtomicInteger audioChunks = new AtomicInteger();
        private final AtomicBoolean recognitionStarted = new AtomicBoolean();
        private final AtomicBoolean ended = new AtomicBoolean();
        private final AtomicBoolean counted = new AtomicBoolean();
        private final AtomicBoolean browserClosing = new AtomicBoolean();
        private volatile WebSocketSession upstream;

        private ProxyState(WebSocketSession browser, long maxAudioBytes) {
            this.browser = browser;
            this.maxAudioBytes = maxAudioBytes;
        }
    }
}
