package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiInterviewProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GladiaLiveSessionClient {
    private final AiInterviewProperties properties;
    private final RestClient.Builder restClientBuilder;

    public GladiaLiveSessionClient(AiInterviewProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    public LiveSession start(int sampleRate, GladiaTranscriptionContext context) {
        try {
            Map<?, ?> response = buildClient().post()
                    .uri("/v2/live")
                    .header("x-gladia-key", properties.getGladiaApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(liveSessionBody(sampleRate, context))
                    .retrieve()
                    .body(Map.class);
            String id = text(response == null ? null : response.get("id"));
            String url = text(response == null ? null : response.get("url"));
            if (id == null || url == null) {
                throw new AiProviderException("GLADIA_LIVE_INVALID_RESPONSE",
                        "Gladia Live không trả về session hợp lệ");
            }
            validateWebsocketUrl(url);
            return new LiveSession(id, url);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiProviderException("GLADIA_LIVE_START_FAILED",
                    "Không thể khởi tạo phiên Gladia Live");
        }
    }

    Map<String, Object> liveSessionBody(int sampleRate, GladiaTranscriptionContext context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("encoding", "wav/pcm");
        body.put("sample_rate", sampleRate);
        body.put("bit_depth", 16);
        body.put("channels", 1);
        body.put("endpointing", 0.8);
        body.put("maximum_duration_without_endpointing", 30);

        List<String> languages = properties.getGladiaLanguages() == null
                ? List.of("vi", "en")
                : properties.getGladiaLanguages().stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
        body.put("language_config", Map.of(
                "languages", languages.isEmpty() ? List.of("vi", "en") : languages,
                "code_switching", properties.isGladiaCodeSwitchingEnabled()
        ));
        body.put("messages_config", Map.of(
                "receive_partial_transcripts", true,
                "receive_final_transcripts", true,
                "receive_speech_events", true,
                "receive_errors", true,
                "receive_lifecycle_events", true
        ));

        GladiaTranscriptionContext safeContext = context == null
                ? GladiaTranscriptionContext.empty() : context;
        if (properties.isGladiaCustomVocabularyEnabled() && !safeContext.vocabulary().isEmpty()) {
            body.put("realtime_processing", Map.of(
                    "custom_vocabulary", true,
                    "custom_vocabulary_config", Map.of(
                            "vocabulary", safeContext.vocabulary(),
                            "default_intensity", properties.getGladiaCustomVocabularyIntensity()
                    )
            ));
        }
        return body;
    }

    private void validateWebsocketUrl(String value) {
        try {
            URI websocket = URI.create(value);
            URI configured = URI.create(trimTrailingSlash(properties.getGladiaBaseUrl()));
            if (!"wss".equalsIgnoreCase(websocket.getScheme())
                    || websocket.getHost() == null
                    || configured.getHost() == null
                    || !websocket.getHost().equalsIgnoreCase(configured.getHost())) {
                throw new AiProviderException("GLADIA_LIVE_INVALID_URL",
                        "Gladia Live trả về WebSocket URL không hợp lệ");
            }
        } catch (IllegalArgumentException exception) {
            throw new AiProviderException("GLADIA_LIVE_INVALID_URL",
                    "Gladia Live trả về WebSocket URL không hợp lệ");
        }
    }

    private RestClient buildClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getProviderConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getProviderReadTimeoutMs()));
        return restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(trimTrailingSlash(properties.getGladiaBaseUrl()))
                .build();
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null || value.isBlank() ? "https://api.gladia.io" : value.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String text(Object value) {
        if (!(value instanceof String text) || text.isBlank()) return null;
        return text.trim();
    }

    public record LiveSession(String id, String websocketUrl) {
    }
}
