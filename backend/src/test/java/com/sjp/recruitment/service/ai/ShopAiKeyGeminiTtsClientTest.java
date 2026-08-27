package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShopAiKeyGeminiTtsClientTest {

    private HttpServer server;
    private AiInterviewProperties properties;
    private ShopAiKeyGeminiTtsClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        properties = new AiInterviewProperties();
        properties.setShopaikeyApiKey("test-key");
        properties.setVoiceProvider("shopaikey_gemini_stream");
        properties.setTtsBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setTtsModel("gemini-3.1-flash-tts-preview");
        properties.setTtsVoice("Kore");
        properties.setTtsVoiceInstruction("Speak naturally in Vietnamese. Do not change the transcript.");
        properties.setTtsConnectTimeoutMs(500);
        properties.setTtsFirstAudioTimeoutMs(1_000);
        properties.setTtsIdleTimeoutMs(500);
        client = new ShopAiKeyGeminiTtsClient(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void streamsPcmWhenOneSseEventIsSplitAcrossNetworkWrites() {
        byte[] first = new byte[]{0, 0, 1, 0};
        byte[] second = new byte[]{2, 0, 3, 0};
        server.createContext(expectedPath(), exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-key");
            assertThat(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    .contains("responseModalities", "AUDIO", "Kore", "Transcript");
            startSse(exchange);
            String event = audioEvent(first);
            int split = event.length() / 2;
            exchange.getResponseBody().write(event.substring(0, split).getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            exchange.getResponseBody().write(event.substring(split).getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().write(audioEvent(second, true).getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ShopAiKeyGeminiTtsClient.StreamMetrics metrics = client.streamSpeech("Câu hỏi Java", output);

        assertThat(output.toByteArray()).containsExactly(0, 0, 1, 0, 2, 0, 3, 0);
        assertThat(metrics.pcmBytes()).isEqualTo(8);
        assertThat(metrics.firstAudioMillis()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.mimeType()).startsWith("audio/l16");
    }

    @Test
    void failsFastWhenProviderDoesNotSendFirstAudio() {
        properties.setTtsFirstAudioTimeoutMs(120);
        client.shutdown();
        client = new ShopAiKeyGeminiTtsClient(properties, new ObjectMapper());
        server.createContext(expectedPath(), exchange -> {
            startSse(exchange);
            try {
                Thread.sleep(500);
                exchange.getResponseBody().write("data: {}\n\n".getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Expected when the client closes the timed-out stream.
            } finally {
                exchange.close();
            }
        });

        long started = System.nanoTime();
        assertThatThrownBy(() -> client.streamSpeech("Câu hỏi", new ByteArrayOutputStream()))
                .isInstanceOfSatisfying(AiProviderException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("GEMINI_TTS_FIRST_AUDIO_TIMEOUT"));
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(450);
    }

    @Test
    void rejectsUnexpectedAudioMimeType() {
        server.createContext(expectedPath(), exchange -> {
            startSse(exchange);
            String data = Base64.getEncoder().encodeToString(new byte[]{0, 0});
            String event = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":"
                    + "{\"mimeType\":\"audio/mp3\",\"data\":\"" + data + "\"}}]}}]}\n\n";
            exchange.getResponseBody().write(event.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        assertThatThrownBy(() -> client.streamSpeech("Câu hỏi", new ByteArrayOutputStream()))
                .isInstanceOfSatisfying(AiProviderException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("GEMINI_TTS_UNSUPPORTED_AUDIO"));
    }

    @Test
    void acceptsDoneMarkerAsTerminalEvent() {
        byte[] pcm = new byte[]{0, 0, 1, 0};
        server.createContext(expectedPath(), exchange -> {
            startSse(exchange);
            exchange.getResponseBody().write(audioEvent(pcm).getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ShopAiKeyGeminiTtsClient.StreamMetrics metrics = client.streamSpeech("Câu hỏi", output);

        assertThat(output.toByteArray()).containsExactly(pcm);
        assertThat(metrics.pcmBytes()).isEqualTo(pcm.length);
    }

    @Test
    void rejectsCleanEofWithoutTerminalEvent() {
        server.createContext(expectedPath(), exchange -> {
            startSse(exchange);
            exchange.getResponseBody().write(audioEvent(new byte[]{0, 0}).getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        assertThatThrownBy(() -> client.streamSpeech("Câu hỏi", new ByteArrayOutputStream()))
                .isInstanceOfSatisfying(AiProviderException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("GEMINI_TTS_INCOMPLETE_STREAM"));
    }

    @Test
    void reportsIdleTimeoutAfterPartialAudio() {
        properties.setTtsIdleTimeoutMs(120);
        client.shutdown();
        client = new ShopAiKeyGeminiTtsClient(properties, new ObjectMapper());
        server.createContext(expectedPath(), exchange -> {
            startSse(exchange);
            try {
                exchange.getResponseBody().write(audioEvent(new byte[]{0, 0}).getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Expected when the watchdog closes the upstream stream.
            } finally {
                exchange.close();
            }
        });

        assertThatThrownBy(() -> client.streamSpeech("Câu hỏi", new ByteArrayOutputStream()))
                .isInstanceOfSatisfying(AiProviderException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("GEMINI_TTS_IDLE_TIMEOUT"));
    }

    @Test
    void retriesOneCompletedEmptyResponseWithinTheOriginalDeadline() {
        AtomicInteger attempts = new AtomicInteger();
        byte[] pcm = new byte[]{0, 0, 1, 0};
        server.createContext(expectedPath(), exchange -> {
            exchange.getRequestBody().readAllBytes();
            startSse(exchange);
            if (attempts.incrementAndGet() == 1) {
                exchange.getResponseBody().write(emptyTerminalEvent().getBytes(StandardCharsets.UTF_8));
            } else {
                exchange.getResponseBody().write(audioEvent(pcm, true).getBytes(StandardCharsets.UTF_8));
            }
            exchange.close();
        });

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ShopAiKeyGeminiTtsClient.StreamMetrics metrics = client.streamSpeech("Câu hỏi", output);

        assertThat(attempts).hasValue(2);
        assertThat(output.toByteArray()).containsExactly(pcm);
        assertThat(metrics.pcmBytes()).isEqualTo(pcm.length);
    }

    @Test
    void failsAfterTwoCompletedEmptyResponses() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext(expectedPath(), exchange -> {
            attempts.incrementAndGet();
            startSse(exchange);
            exchange.getResponseBody().write(emptyTerminalEvent().getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        assertThatThrownBy(() -> client.streamSpeech("Câu hỏi", new ByteArrayOutputStream()))
                .isInstanceOfSatisfying(AiProviderException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("GEMINI_TTS_EMPTY_AUDIO");
                    assertThat(exception.getMessage()).contains("finishReason=STOP");
                });
        assertThat(attempts).hasValue(2);
    }

    @Test
    void doesNotRetryProviderBlockedResponse() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext(expectedPath(), exchange -> {
            attempts.incrementAndGet();
            startSse(exchange);
            exchange.getResponseBody().write(("data: {\"promptFeedback\":{"
                    + "\"blockReason\":\"SAFETY\"}}\n\n").getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        assertThatThrownBy(() -> client.streamSpeech("Câu hỏi", new ByteArrayOutputStream()))
                .isInstanceOfSatisfying(AiProviderException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("GEMINI_TTS_PROVIDER_BLOCKED"));
        assertThat(attempts).hasValue(1);
    }

    private String expectedPath() {
        return "/v1beta/models/gemini-3.1-flash-tts-preview:streamGenerateContent";
    }

    private void startSse(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
    }

    private String audioEvent(byte[] pcm) {
        return audioEvent(pcm, false);
    }

    private String audioEvent(byte[] pcm, boolean terminal) {
        String data = Base64.getEncoder().encodeToString(pcm);
        return "data: {\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":"
                + "{\"mimeType\":\"audio/l16; rate=24000; channels=1\",\"data\":\"" + data
                + "\"}}]}"
                + (terminal ? ",\"finishReason\":\"STOP\"" : "")
                + "}]}\n\n";
    }

    private String emptyTerminalEvent() {
        return "data: {\"candidates\":[{\"finishReason\":\"STOP\"}]}\n\n";
    }
}
