package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiInterviewProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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
            InputStreamResource resource = new InputStreamResource(file.getInputStream()) {
                @Override public String getFilename() {
                    return file.getOriginalFilename() == null ? "answer.webm" : file.getOriginalFilename();
                }
                @Override public long contentLength() { return file.getSize(); }
            };
            String audioUrl = uploadAudio(client, resource, resource.getFilename(), file.getSize(), file.getContentType());
            String transcriptionId = startTranscription(client, audioUrl, GladiaTranscriptionContext.empty());
            return pollTranscript(client, transcriptionId);
        } catch (IOException | RuntimeException exception) {
            throw new AiProviderException("STT_PROVIDER_FAILED", "He thong chua xu ly duoc cau tra loi nay, vui long thu lai.");
        }
    }

    public String transcribe(Path path, String mimeType, GladiaTranscriptionContext context) {
        try {
            RestClient client = buildClient();
            FileSystemResource resource = new FileSystemResource(path);
            String audioUrl = uploadAudio(client, resource, path.getFileName().toString(), Files.size(path), mimeType);
            String transcriptionId = startTranscription(client, audioUrl, context == null ? GladiaTranscriptionContext.empty() : context);
            return pollTranscript(client, transcriptionId);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof AiProviderException providerException) throw providerException;
            throw new AiProviderException("STT_PROVIDER_FAILED", "He thong chua xu ly duoc cau tra loi nay, vui long thu lai.");
        }
    }

    public String transcribe(byte[] audio, String filename, String mimeType, GladiaTranscriptionContext context) {
        if (audio == null || audio.length == 0) {
            throw new AiProviderException("STT_EMPTY_AUDIO", "Khong co audio de gui sang Gladia");
        }
        try {
            RestClient client = buildClient();
            InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(audio)) {
                @Override public String getFilename() {
                    return filename == null || filename.isBlank() ? "answer.wav" : filename;
                }
                @Override public long contentLength() { return audio.length; }
            };
            String audioUrl = uploadAudio(client, resource, resource.getFilename(), audio.length, mimeType);
            String transcriptionId = startTranscription(client, audioUrl,
                    context == null ? GladiaTranscriptionContext.empty() : context);
            return pollTranscript(client, transcriptionId);
        } catch (RuntimeException exception) {
            if (exception instanceof AiProviderException providerException) throw providerException;
            throw new AiProviderException("STT_PROVIDER_FAILED",
                    "He thong chua xu ly duoc cau tra loi nay, vui long thu lai.");
        }
    }

    private String uploadAudio(RestClient client, Resource resource, String filename, long size, String mimeType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("audio", resource)
                .filename(filename == null ? "answer.webm" : filename)
                .contentType(MediaType.parseMediaType(mimeType == null || mimeType.isBlank() ? "audio/webm" : mimeType));

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

    private String startTranscription(RestClient client, String audioUrl, GladiaTranscriptionContext context) {
        Map<String, Object> body = transcriptionBody(audioUrl, context, true);
        try {
            return postTranscription(client, body);
        } catch (HttpClientErrorException.BadRequest exception) {
            if (!hasEnhancements(context)) throw exception;
            return postTranscription(client, transcriptionBody(audioUrl, GladiaTranscriptionContext.empty(), false));
        }
    }

    private String postTranscription(RestClient client, Map<String, Object> body) {
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

    Map<String, Object> transcriptionBody(
            String audioUrl, GladiaTranscriptionContext context, boolean includeEnhancements) {
        Map<String, Object> languageConfig = new LinkedHashMap<>();
        List<String> languages = properties.getGladiaLanguages() == null
                ? List.of("vi", "en")
                : properties.getGladiaLanguages().stream().filter(value -> value != null && !value.isBlank()).toList();
        languageConfig.put("languages", languages);
        if (includeEnhancements) {
            languageConfig.put("code_switching", properties.isGladiaCodeSwitchingEnabled());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("audio_url", audioUrl);
        body.put("language_config", languageConfig);
        if (includeEnhancements && properties.isGladiaCustomVocabularyEnabled() && !context.vocabulary().isEmpty()) {
            body.put("custom_vocabulary", true);
            body.put("custom_vocabulary_config", Map.of(
                    "vocabulary", context.vocabulary(),
                    "default_intensity", properties.getGladiaCustomVocabularyIntensity()
            ));
        }
        return body;
    }

    private boolean hasEnhancements(GladiaTranscriptionContext context) {
        return properties.isGladiaCodeSwitchingEnabled()
                || (properties.isGladiaCustomVocabularyEnabled() && !context.vocabulary().isEmpty());
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
