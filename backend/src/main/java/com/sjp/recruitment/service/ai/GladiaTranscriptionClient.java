package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiInterviewProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GladiaTranscriptionClient {

    private static final int MAX_POLL_ATTEMPTS = 30;
    private static final Duration POLL_DELAY = Duration.ofSeconds(2);

    private final AiInterviewProperties properties;
    private final RestClient.Builder restClientBuilder;

    public String transcribe(MultipartFile file) {
        try {
            RestClient client = buildClient();
            String audioUrl = uploadAudio(client, file);
            String transcriptionId = startTranscription(client, audioUrl);
            return pollTranscript(client, transcriptionId);
        } catch (IOException | RuntimeException exception) {
            throw new AiProviderException("STT_PROVIDER_FAILED", "He thong chua xu ly duoc cau tra loi nay, vui long thu lai.");
        }
    }

    private String uploadAudio(RestClient client, MultipartFile file) throws IOException {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        InputStreamResource resource = new InputStreamResource(file.getInputStream()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename() == null ? "answer.webm" : file.getOriginalFilename();
            }

            @Override
            public long contentLength() {
                return file.getSize();
            }
        };
        builder.part("audio", resource)
                .filename(resource.getFilename())
                .contentType(MediaType.parseMediaType(file.getContentType() == null ? "audio/webm" : file.getContentType()));

        Map<?, ?> response = client.post()
                .uri("/v2/upload")
                .header("x-gladia-key", properties.getGladiaApiKey())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(Map.class);

        Object audioUrl = response == null ? null : response.get("audio_url");
        if (!(audioUrl instanceof String value) || value.isBlank()) {
            throw new AiProviderException("STT_UPLOAD_FAILED", "Khong nhan duoc audio_url tu Gladia");
        }
        return value;
    }

    private String startTranscription(RestClient client, String audioUrl) {
        Map<String, Object> body = Map.of(
                "audio_url", audioUrl,
                "language_config", Map.of("languages", List.of("vi"))
        );
        Map<?, ?> response = client.post()
                .uri("/v2/pre-recorded")
                .header("x-gladia-key", properties.getGladiaApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        Object id = response == null ? null : response.get("id");
        if (!(id instanceof String value) || value.isBlank()) {
            throw new AiProviderException("STT_START_FAILED", "Khong nhan duoc transcription id tu Gladia");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private String pollTranscript(RestClient client, String id) {
        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            Map<?, ?> response = client.get()
                    .uri("/v2/pre-recorded/{id}", id)
                    .header("x-gladia-key", properties.getGladiaApiKey())
                    .retrieve()
                    .body(Map.class);
            String status = response == null ? null : String.valueOf(response.get("status"));
            if ("done".equalsIgnoreCase(status)) {
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                Map<String, Object> transcription = result == null ? null : (Map<String, Object>) result.get("transcription");
                Object transcript = transcription == null ? null : transcription.get("full_transcript");
                if (transcript instanceof String value && !value.isBlank()) {
                    return value;
                }
                throw new AiProviderException("STT_EMPTY_TRANSCRIPT", "Gladia khong tra transcript");
            }
            if ("error".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status)) {
                throw new AiProviderException("STT_FAILED", "Gladia xu ly that bai");
            }
            sleep();
        }
        throw new AiProviderException("STT_TIMEOUT", "Gladia xu ly qua lau");
    }

    private void sleep() {
        try {
            Thread.sleep(POLL_DELAY.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("STT_INTERRUPTED", "Qua trinh xu ly bi gian doan");
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://api.gladia.io";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
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
}
