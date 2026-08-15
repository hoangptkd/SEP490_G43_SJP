package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiInterviewProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GladiaLiveSessionClientConfigurationTest {
    @Test
    @SuppressWarnings("unchecked")
    void buildsPcmCodeSwitchingPartialFinalAndVocabularyRequest() {
        AiInterviewProperties properties = new AiInterviewProperties();
        properties.setGladiaLanguages(List.of("vi", "en"));
        properties.setGladiaCodeSwitchingEnabled(true);
        properties.setGladiaCustomVocabularyEnabled(true);
        GladiaLiveSessionClient client = new GladiaLiveSessionClient(properties, RestClient.builder());

        Map<String, Object> body = client.liveSessionBody(48_000,
                new GladiaTranscriptionContext(List.of("Spring Boot", "REST API")));

        assertThat(body)
                .containsEntry("encoding", "wav/pcm")
                .containsEntry("sample_rate", 48_000)
                .containsEntry("bit_depth", 16)
                .containsEntry("channels", 1);
        assertThat((Map<String, Object>) body.get("language_config"))
                .containsEntry("languages", List.of("vi", "en"))
                .containsEntry("code_switching", true);
        assertThat((Map<String, Object>) body.get("messages_config"))
                .containsEntry("receive_partial_transcripts", true)
                .containsEntry("receive_final_transcripts", true);
        Map<String, Object> realtime = (Map<String, Object>) body.get("realtime_processing");
        assertThat(realtime).containsEntry("custom_vocabulary", true);
        assertThat((Map<String, Object>) realtime.get("custom_vocabulary_config"))
                .containsEntry("vocabulary", List.of("Spring Boot", "REST API"));
    }
}
