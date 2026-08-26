package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.model.entity.Job;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopAiKeyJobSearchClientTest {
    @Test
    void sendsCvAndUnrankedJobDataAndReadsStructuredAiEvaluation() throws Exception {
        try (var fixture = new ProviderFixture("{\"items\":[{\"matchScore\":83}]}", "stop")) {
            var result = fixture.client.rank(fixture.context, fixture.candidates);
            assertEquals(83, result.path("items").get(0).path("matchScore").asInt());
            assertEquals("Bearer test-only-key", fixture.authorization.get());
            assertTrue(fixture.requestBody.get().toString().contains("cvContent"));
            assertFalse(fixture.requestBody.get().toString().contains("backendMatchScore"));
        }
    }

    @Test
    void correctionIncludesPriorJsonAndSpecificValidationFeedback() throws Exception {
        try (var fixture = new ProviderFixture("{\"items\":[]}", "stop")) {
            JsonNode previous = fixture.mapper.readTree("{\"items\":[{\"reason\":\"Prior response\"}]}");
            var failure = new AiJobSearchValidationException("JOB_QUOTE_NOT_FOUND", "items[0].evidence[0].jobQuote", "Copy exactly.");
            fixture.client.rank(fixture.context, fixture.candidates, previous, failure);

            var messages = fixture.requestBody.get().path("messages");
            assertEquals(4, messages.size());
            assertEquals("assistant", messages.get(2).path("role").asText());
            assertEquals(previous, fixture.mapper.readTree(messages.get(2).path("content").asText()));
            String feedback = messages.get(3).path("content").asText();
            assertTrue(feedback.contains(failure.getCode()));
            assertTrue(feedback.contains(failure.getPath()));
            assertTrue(feedback.contains(failure.getMessage()));
        }
    }

    @Test
    void providesCvGroundedSkillOptionsWithoutInferringSqlFromMysql() throws Exception {
        try (var fixture = new ProviderFixture("{\"items\":[]}", "stop")) {
            String cvText = "Built Java applications using RESTful APIs and MySQL.";
            when(fixture.context.cvText()).thenReturn(cvText);
            when(fixture.context.providerContext()).thenReturn(Map.of("cvContent", cvText));
            when(fixture.candidates.get(0).job().getSkills()).thenReturn(List.of("Java", "REST API", "SQL"));
            fixture.client.rank(fixture.context, fixture.candidates);
            String prompt = fixture.requestBody.get().path("messages").get(1).path("content").asText();
            String jobJson = prompt.split("CandidateJobs=", 2)[1].split("\n", 2)[0];
            JsonNode job = fixture.mapper.readTree(jobJson).get(0);
            assertEquals(fixture.mapper.valueToTree(List.of("Java", "REST API")), job.path("matchedSkillOptions"));
            assertEquals(fixture.mapper.valueToTree(List.of("SQL")), job.path("missingSkillOptions"));
            // Guidance uses the same matching rules as validation, rather than a second heuristic.
            var item = fixture.mapper.createObjectNode();
            item.put("jobId", fixture.candidates.get(0).job().getId().toString());
            item.put("matchScore", 70);
            item.set("matchedSkills", job.path("matchedSkillOptions"));
            item.set("missingSkills", job.path("missingSkillOptions"));
            item.put("reason", "Relevant Java experience.");
            item.putArray("evidence").addObject().put("cvQuote", "Built Java applications").put("jobQuote", "Java Developer");
            var response = fixture.mapper.createObjectNode();
            response.putArray("items").add(item);
            assertEquals(1, new AiJobSearchResultValidator(new AiJobSearchProperties())
                    .validate(response, fixture.context, fixture.candidates).size());
        }
    }

    @Test
    void quoteOptionsRemainBoundedContiguousSourceText() throws Exception {
        String source = "Develop Java applications. " + "Maintain RESTful APIs and review changes ".repeat(20);
        var options = AiJobSearchEvidenceQuotes.options(4, source);
        assertFalse(options.isEmpty());
        assertTrue(options.size() <= 4);
        assertTrue(options.stream().allMatch(quote -> quote.length() >= 5 && quote.length() <= 180 && source.contains(quote)));
        assertTrue(AiJobSearchEvidenceQuotes.options(4, null, "", "SQL").isEmpty());
        try (var fixture = new ProviderFixture("{\"items\":[]}", "stop")) {
            when(fixture.candidates.get(0).job().getDescription()).thenReturn(source);
            fixture.client.rank(fixture.context, fixture.candidates);
            String prompt = fixture.requestBody.get().path("messages").get(1).path("content").asText();
            JsonNode cvOptions = fixture.mapper.readTree(prompt.split("CvQuoteOptions=", 2)[1].split("\n", 2)[0]);
            assertEquals("Java developer building REST APIs", cvOptions.get(0).asText());
            JsonNode job = fixture.mapper.readTree(prompt.split("CandidateJobs=", 2)[1].split("\n", 2)[0]).get(0);
            assertEquals("Java Developer", job.path("jobQuoteOptions").get(0).asText());
            for (JsonNode quote : job.path("jobQuoteOptions")) {
                assertTrue(quote.asText().length() <= 180);
                assertTrue(quote.asText().equals("Java Developer") || source.contains(quote.asText()));
            }
        }
    }

    @Test
    void rejectsTruncatedResponseEvenIfItsPartialJsonCanBeParsed() throws Exception {
        try (var fixture = new ProviderFixture("{\"items\":[]}", "length")) {
            var failure = assertThrows(AiJobSearchValidationException.class,
                    () -> fixture.client.rank(fixture.context, fixture.candidates));
            assertEquals("OUTPUT_TRUNCATED", failure.getCode());
            assertEquals("items", failure.getPath());
        }
    }

    @Test
    void resolvesProviderEvidenceIndicesToExactSourceQuotesBeforeValidation() throws Exception {
        String answer = """
                {"items":[{"jobId":"$JOB_ID","matchScore":80,"matchedSkills":["Java"],"missingSkills":[],
                "reason":"Relevant Java experience.","evidence":[{"cvQuoteIndex":0,"jobQuoteIndex":0,
                "cvQuote":"Invented text","jobQuote":"Java"}]}]}
                """;
        try (var fixture = new ProviderFixture(answer, "stop")) {
            var response = fixture.client.rank(fixture.context, fixture.candidates);
            var evidence = response.path("items").get(0).path("evidence").get(0);
            assertEquals("Java developer building REST APIs", evidence.path("cvQuote").asText());
            assertEquals("Java Developer", evidence.path("jobQuote").asText());
            assertEquals(1, new AiJobSearchResultValidator(new AiJobSearchProperties())
                    .validate(response, fixture.context, fixture.candidates).size());
        }
    }

    @Test
    void rejectsInvalidEvidenceReferencesWithoutInventingOrClampingQuotes() throws Exception {
        for (String pair : List.of("{\"cvQuoteIndex\":-1,\"jobQuoteIndex\":0}",
                "{\"cvQuoteIndex\":0,\"jobQuoteIndex\":10}", "{\"cvQuoteIndex\":\"0\",\"jobQuoteIndex\":0}",
                "{\"cvQuoteIndex\":0}", "{\"cvQuoteIndex\":0.5,\"jobQuoteIndex\":0}")) {
            String answer = "{\"items\":[{\"jobId\":\"$JOB_ID\",\"evidence\":[" + pair + "]}]}";
            try (var fixture = new ProviderFixture(answer, "stop")) {
                var failure = assertThrows(AiJobSearchValidationException.class,
                        () -> fixture.client.rank(fixture.context, fixture.candidates));
                assertEquals("EVIDENCE_REFERENCE", failure.getCode());
                assertTrue(failure.getPath().startsWith("items[0].evidence[0]."));
            }
        }
    }

    @Test
    void malformedJsonCanRetryWithoutReplayingInvalidContent() throws Exception {
        try (var fixture = new ProviderFixture("{not-json}", "stop")) {
            var failure = assertThrows(AiJobSearchValidationException.class,
                    () -> fixture.client.rank(fixture.context, fixture.candidates));
            assertEquals("INVALID_JSON", failure.getCode());
            assertFalse(failure.getMessage().contains("not-json"));
            assertThrows(AiJobSearchValidationException.class,
                    () -> fixture.client.rank(fixture.context, fixture.candidates, null, failure));
            var messages = fixture.requestBody.get().path("messages");
            assertEquals(3, messages.size());
            assertEquals("user", messages.get(2).path("role").asText());
            assertTrue(messages.get(2).path("content").asText().contains("INVALID_JSON"));
        }
    }

    private static class ProviderFixture implements AutoCloseable {
        final ObjectMapper mapper = new ObjectMapper();
        final AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        final AtomicReference<String> authorization = new AtomicReference<>();
        final AiJobSearchContext context = mock(AiJobSearchContext.class);
        final List<AiJobSearchCandidateSelector.SelectedJob> candidates;
        final ShopAiKeyJobSearchClient client;
        final HttpServer server;

        ProviderFixture(String answer, String finishReason) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var properties = new AiJobSearchProperties();
            properties.setShopaikeyBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            properties.setShopaikeyApiKey("test-only-key");
            when(context.providerContext()).thenReturn(Map.of("cvContent", "Java developer building REST APIs"));
            when(context.cvText()).thenReturn("Java developer building REST APIs");
            var job = mock(Job.class);
            when(job.getId()).thenReturn(UUID.randomUUID());
            when(job.getTitle()).thenReturn("Java Developer");
            when(job.getSkills()).thenReturn(List.of("Java"));
            candidates = List.of(new AiJobSearchCandidateSelector.SelectedJob(job, 10));
            client = new ShopAiKeyJobSearchClient(RestClient.builder(), mapper, properties);
            byte[] response = mapper.writeValueAsBytes(Map.of("choices", List.of(Map.of(
                    "message", Map.of("content", answer.replace("$JOB_ID", job.getId().toString())), "finish_reason", finishReason))));
            server.createContext("/v1/chat/completions", exchange -> {
                requestBody.set(mapper.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            server.start();
        }

        @Override
        public void close() { server.stop(0); }
    }
}
