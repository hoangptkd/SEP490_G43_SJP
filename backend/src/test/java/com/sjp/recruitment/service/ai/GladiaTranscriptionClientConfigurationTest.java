package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiInterviewProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GladiaTranscriptionClientConfigurationTest {
    @Test
    @SuppressWarnings("unchecked")
    void buildsVietnameseEnglishCodeSwitchingAndContextVocabularyRequest() {
        AiInterviewProperties properties = new AiInterviewProperties();
        properties.setGladiaLanguages(List.of("vi", "en"));
        properties.setGladiaCodeSwitchingEnabled(true);
        properties.setGladiaCustomVocabularyEnabled(true);
        GladiaTranscriptionClient client = new GladiaTranscriptionClient(properties, RestClient.builder());

        Map<String, Object> body = client.transcriptionBody("https://audio.example/test.wav",
                new GladiaTranscriptionContext(List.of("Spring Boot", "REST API")), true);

        assertThat((Map<String, Object>) body.get("language_config"))
                .containsEntry("languages", List.of("vi", "en"))
                .containsEntry("code_switching", true);
        assertThat(body).containsEntry("custom_vocabulary", true);
        assertThat((Map<String, Object>) body.get("custom_vocabulary_config"))
                .containsEntry("vocabulary", List.of("Spring Boot", "REST API"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsSafeRetryRequestWithoutOptionalEnhancements() {
        AiInterviewProperties properties = new AiInterviewProperties();
        GladiaTranscriptionClient client = new GladiaTranscriptionClient(properties, RestClient.builder());

        Map<String, Object> body = client.transcriptionBody("https://audio.example/test.wav",
                GladiaTranscriptionContext.empty(), false);

        assertThat((Map<String, Object>) body.get("language_config")).doesNotContainKey("code_switching");
        assertThat(body).doesNotContainKeys("custom_vocabulary", "custom_vocabulary_config");
    }
}
