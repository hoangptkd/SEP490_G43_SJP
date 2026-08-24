package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiInterviewProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiInterviewSpeechCacheTest {

    @Test
    void returnsDefensiveCopiesAndKeepsConfiguredEntryLimit() {
        AiInterviewProperties properties = new AiInterviewProperties();
        properties.setTtsCacheMaxEntries(2);
        properties.setTtsCacheTtlSeconds(60);
        AiInterviewSpeechCache cache = new AiInterviewSpeechCache(properties);

        byte[] original = new byte[]{1, 2};
        cache.put("one", original);
        original[0] = 9;
        byte[] cached = cache.get("one");
        assertThat(cached).containsExactly(1, 2);
        cached[1] = 8;
        assertThat(cache.get("one")).containsExactly(1, 2);

        cache.put("two", new byte[]{3, 4});
        cache.put("three", new byte[]{5, 6});
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void keyChangesWithVoiceConfiguration() {
        AiInterviewProperties properties = new AiInterviewProperties();
        AiInterviewSpeechCache cache = new AiInterviewSpeechCache(properties);
        String first = cache.key("Câu hỏi Java");

        properties.setTtsVoice("Puck");

        assertThat(cache.key("Câu hỏi Java")).isNotEqualTo(first);
    }

    @Test
    void composesExactConversationSpeechFromCachedSegments() {
        AiInterviewProperties properties = new AiInterviewProperties();
        AiInterviewSpeechCache cache = new AiInterviewSpeechCache(properties);
        cache.put(cache.key("Chào bạn."), new byte[]{1, 0});
        cache.put(cache.key("Bạn hãy giới thiệu kinh nghiệm Java."), new byte[]{2, 0});

        byte[] speech = cache.getSpeech(
                "  Chào   bạn. \r\n\r\n Bạn hãy giới thiệu kinh nghiệm Java.  ");

        assertThat(speech).containsExactly(1, 0, 2, 0);
        assertThat(cache.get(cache.key(
                "Chào bạn.\nBạn hãy giới thiệu kinh nghiệm Java.")))
                .containsExactly(1, 0, 2, 0);
    }
}
