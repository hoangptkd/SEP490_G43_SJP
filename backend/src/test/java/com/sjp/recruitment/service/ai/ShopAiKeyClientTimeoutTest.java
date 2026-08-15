package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.service.AiInterviewTelemetryService;
import com.sjp.recruitment.service.SystemSettingsService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)
class ShopAiKeyClientTimeoutTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsReducedInitialQuestionTokenLimit() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            respondJson(exchange, successfulInterviewPackageResponse());
        });
        AiInterviewTelemetryService telemetry = mock(AiInterviewTelemetryService.class);
        ShopAiKeyClient client = client(telemetry, 2_000);

        ShopAiKeyClient.InterviewPackageDraft result = client.generateInitialInterviewPackage(
                practiceSession(), new CandidateProfile());

        assertEquals(ShopAiKeyClient.INITIAL_QUESTION_MAX_TOKENS,
                requestBody.get().path("max_tokens").asInt());
        assertEquals(1_800, requestBody.get().path("max_tokens").asInt());
        assertEquals(3, result.questions().size());
    }

    @Test
    void mapsReadTimeoutAndRecordsSpecificTelemetryCode() throws Exception {
        startServer(exchange -> {
            try {
                Thread.sleep(300);
                respondJson(exchange, successfulInterviewPackageResponse());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client closes the timed-out connection before the delayed response is written.
            }
        });
        AiInterviewTelemetryService telemetry = mock(AiInterviewTelemetryService.class);
        ShopAiKeyClient client = client(telemetry, 50);
        InterviewSession session = practiceSession();

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> client.generateInitialInterviewPackage(session, new CandidateProfile()));

        assertEquals("AI_PROVIDER_TIMEOUT", exception.getCode());
        assertTrue(exception.getMessage().contains("quá thời gian"));
        verify(telemetry, times(ShopAiKeyClient.PROVIDER_MAX_ATTEMPTS)).record(
                eq(session.getId()), eq("initial_questions"), eq("gpt-4.1-mini"),
                eq("ai-question-initial-v2"), isNull(), isNull(), anyLong(),
                eq(false), eq("AI_PROVIDER_TIMEOUT")
        );
    }

    @Test
    void classifiesNonTimeoutResourceFailureAsConnectionFailure() {
        AiProviderException exception = ShopAiKeyClient.mapProviderException(
                new ResourceAccessException("Connection reset", new IOException("Connection reset")));

        assertEquals("AI_PROVIDER_CONNECTION_FAILED", exception.getCode());
    }

    @Test
    void retriesHttp5xxOnceAndThenUsesSuccessfulResponse(CapturedOutput output) throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            if (requestCount.incrementAndGet() == 1) {
                respondJson(exchange, 503, "{\"error\":\"temporary overload\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            respondJson(exchange, successfulInterviewPackageResponse());
        });
        ShopAiKeyClient client = client(mock(AiInterviewTelemetryService.class), 2_000);

        ShopAiKeyClient.InterviewPackageDraft result = client.generateInitialInterviewPackage(
                practiceSession(), new CandidateProfile());

        assertEquals(2, requestCount.get());
        assertEquals(3, result.questions().size());
        assertTrue(output.getAll().contains("code=AI_PROVIDER_HTTP_ERROR"));
        assertTrue(output.getAll().contains("httpStatus=503"));
        assertTrue(output.getAll().contains("retryScheduled=true"));
        assertTrue(output.getAll().contains("temporary overload"));
    }

    @Test
    void classifiesUnexpectedProviderShapeSeparately() {
        AiProviderException exception = ShopAiKeyClient.mapProviderException(
                new ClassCastException("choices must be an array"));

        assertEquals("AI_INVALID_PROVIDER_RESPONSE", exception.getCode());
    }

    @Test
    void recognizesNestedSocketTimeout() {
        AiProviderException exception = ShopAiKeyClient.mapProviderException(
                new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out")));

        assertEquals("AI_PROVIDER_TIMEOUT", exception.getCode());
    }

    @Test
    void analyzesOneTurnWithTypeSpecificEvidenceAndCompactTokenBudget() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            respondJson(exchange, chatResponse("""
                    {
                      "action":"PROBE",
                      "keyClaims":["đã kiểm tra execution plan"],
                      "evidenceCoverage":{
                        "accuracy":true,
                        "reasoning":true,
                        "tradeOffs":false,
                        "implementationDetail":true,
                        "realWorldApplication":true
                      },
                      "missingEvidence":["trade-off của index"],
                      "followUp":"Bạn đã cân nhắc trade-off nào khi thêm index?",
                      "updatedItemSummary":"Ứng viên đã kiểm tra execution plan và thêm index.",
                      "globalEvidenceDelta":{
                        "demonstratedCompetencyIds":["problem-solving"],
                        "weakEvidence":["chưa nêu trade-off"],
                        "interestingClaims":["đã dùng execution plan"],
                        "unverifiedClaims":[]
                      }
                    }
                    """));
        });
        ShopAiKeyClient client = client(mock(AiInterviewTelemetryService.class), 2_000);
        InterviewSession session = practiceSessionWithProfile();
        InterviewQuestion question = technicalQuestion(session, 1, "problem-solving");

        ShopAiKeyClient.AnswerAnalysisDraft result = client.analyzeAssessmentTurn(
                session,
                question,
                "Tôi kiểm tra execution plan rồi thêm index.",
                Map.of("summary", "Chưa có evidence trước đó"),
                new ShopAiKeyClient.AssessmentTurnCounters(0, 0, 1, 4)
        );

        assertEquals(ShopAiKeyClient.AnswerAnalysisAction.PROBE, result.action());
        assertEquals(800, requestBody.get().path("max_tokens").asInt());
        String serializedRequest = requestBody.get().toString();
        assertTrue(serializedRequest.contains("remainingCoreQuestions"));
        assertTrue(serializedRequest.contains("implementationDetail"));
        assertTrue(serializedRequest.contains("tradeOffs"));
        assertTrue(serializedRequest.contains("dài tối đa 1200"));
        assertTrue(serializedRequest.contains(ShopAiKeyClient.EMPTY_ITEM_SUMMARY));
        assertTrue(serializedRequest.contains("weakEvidence"));
        assertTrue(serializedRequest.contains("tối đa 4 phần tử"));
    }

    @Test
    void usesNeutralSummaryWhenShortAnswerProducesEmptySummary(CapturedOutput output) throws Exception {
        startServer(exchange -> respondJson(exchange, answerAnalysisResponse("", List.of())));
        ShopAiKeyClient client = client(mock(AiInterviewTelemetryService.class), 2_000);
        InterviewSession session = practiceSessionWithProfile();

        ShopAiKeyClient.AnswerAnalysisDraft result = client.analyzeAssessmentTurn(
                session,
                technicalQuestion(session, 1, "problem-solving"),
                "ok",
                Map.of(),
                new ShopAiKeyClient.AssessmentTurnCounters(0, 1, 3, 2)
        );

        assertEquals(ShopAiKeyClient.EMPTY_ITEM_SUMMARY, result.updatedItemSummary());
        assertTrue(output.getAll().contains("reason=EMPTY"));
    }

    @Test
    void preservesPreviousEvidenceSummaryWhenProviderReturnsEmptySummary() throws Exception {
        startServer(exchange -> respondJson(exchange, answerAnalysisResponse("", List.of())));
        ShopAiKeyClient client = client(mock(AiInterviewTelemetryService.class), 2_000);
        InterviewSession session = practiceSessionWithProfile();
        String previousSummary = "Ứng viên đã nêu cách kiểm tra execution plan.";

        ShopAiKeyClient.AnswerAnalysisDraft result = client.analyzeAssessmentTurn(
                session,
                technicalQuestion(session, 1, "problem-solving"),
                "ok",
                Map.of("summary", previousSummary),
                new ShopAiKeyClient.AssessmentTurnCounters(1, 0, 2, 3)
        );

        assertEquals(previousSummary, result.updatedItemSummary());
    }

    @Test
    void truncatesOversizedItemSummaryWithoutFailingAnalysis(CapturedOutput output) throws Exception {
        String oversizedSummary = "Evidence kỹ thuật được mô tả chi tiết. ".repeat(50);
        startServer(exchange -> respondJson(
                exchange,
                answerAnalysisResponse(oversizedSummary, List.of("có evidence kỹ thuật"))));
        ShopAiKeyClient client = client(mock(AiInterviewTelemetryService.class), 2_000);
        InterviewSession session = practiceSessionWithProfile();

        ShopAiKeyClient.AnswerAnalysisDraft result = client.analyzeAssessmentTurn(
                session,
                technicalQuestion(session, 1, "problem-solving"),
                "Tôi kiểm tra execution plan.",
                Map.of(),
                new ShopAiKeyClient.AssessmentTurnCounters(0, 0, 1, 4)
        );

        assertTrue(result.updatedItemSummary().length()
                <= ShopAiKeyClient.UPDATED_ITEM_SUMMARY_MAX_LENGTH);
        assertTrue(!result.updatedItemSummary().isBlank());
        assertTrue(output.getAll().contains("reason=TOO_LONG"));
    }

    @Test
    void correctsBrowserTranscriptWithStrictSchemaAndDedicatedTokenBudget() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            respondJson(exchange, chatResponse("""
                    {
                      "correctedTranscript":"ờ em dùng Spring Boot với Postman",
                      "corrections":[
                        {"original":"spring bút","replacement":"Spring Boot","confidence":0.98,"reason":"cv_term"},
                        {"original":"post men","replacement":"Postman","confidence":0.97,"reason":"current_question"}
                      ],
                      "evidence":[{"type":"action","text":"Sử dụng Spring Boot và Postman"}],
                      "answerSummary":"Ứng viên mô tả công cụ đã sử dụng.",
                      "followUpNeeded":true,
                      "followUpReason":"Chưa nêu kết quả."
                    }
                    """));
        });
        ShopAiKeyClient client = client(mock(AiInterviewTelemetryService.class), 2_000);
        UUID sessionId = UUID.randomUUID();

        ShopAiKeyClient.TranscriptCorrectionDraft result = client.correctBrowserTranscript(
                sessionId,
                new TranscriptCorrectionContext(
                        "Bạn dùng Postman để kiểm thử REST API như thế nào?",
                        "ờ em dùng spring bút với post men",
                        List.of("Spring Boot", "Postman"),
                        List.of("REST API"),
                        List.of("bug", "debug"),
                        "Chưa nêu kết quả"));

        assertEquals("ờ em dùng Spring Boot với Postman", result.correctedTranscript());
        assertEquals(2, result.corrections().size());
        assertEquals("action", result.evidence().get(0).type());
        assertTrue(result.followUpNeeded());
        assertEquals(ShopAiKeyClient.TRANSCRIPT_CORRECTION_MAX_TOKENS,
                requestBody.get().path("max_tokens").asInt());
        assertEquals(0.0, requestBody.get().path("temperature").asDouble());
        String serializedRequest = requestBody.get().toString();
        assertTrue(serializedRequest.contains("cvTechnicalTerms"));
        assertTrue(serializedRequest.contains("relevantTechnicalVocabulary"));
        assertTrue(serializedRequest.contains("False correction"));
    }

    @Test
    void logsSchemaDetailAndProviderJsonForInvalidTranscriptCorrection(CapturedOutput output)
            throws Exception {
        startServer(exchange -> respondJson(exchange, chatResponse("""
                {
                  "correctedTranscript":"em dùng Spring Boot",
                  "corrections":[],
                  "evidence":[],
                  "answerSummary":"Ứng viên dùng Spring Boot.",
                  "followUpNeeded":"true",
                  "followUpReason":"Chưa nêu kết quả."
                }
                """)));
        ShopAiKeyClient client = client(mock(AiInterviewTelemetryService.class), 2_000);

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> client.correctBrowserTranscript(
                        UUID.randomUUID(),
                        new TranscriptCorrectionContext(
                                "Bạn dùng Spring Boot như thế nào?",
                                "em dùng spring bút",
                                List.of("Spring Boot"),
                                List.of(),
                                List.of(),
                                "")));

        assertEquals("AI_INVALID_TRANSCRIPT_CORRECTION_SCHEMA", exception.getCode());
        assertTrue(output.getAll().contains("ShopAIKey response rejected"));
        assertTrue(output.getAll().contains("detail=\"AI"));
        assertTrue(output.getAll().contains("boolean"));
        assertTrue(output.getAll().contains("followUpNeeded"));
        assertTrue(output.getAll().contains("\\\"true\\\""));
    }

    @Test
    void rejectsMarkdownWrappedTranscriptCorrectionResponse() throws Exception {
        startServer(exchange -> respondJson(exchange, chatResponse("""
                ```json
                {
                  "correctedTranscript":"em dùng Spring Boot",
                  "corrections":[],
                  "evidence":[],
                  "answerSummary":"Ứng viên dùng Spring Boot.",
                  "followUpNeeded":false,
                  "followUpReason":null
                }
                ```
                """)));
        ShopAiKeyClient client = client(mock(AiInterviewTelemetryService.class), 2_000);

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> client.correctBrowserTranscript(
                        UUID.randomUUID(),
                        new TranscriptCorrectionContext(
                                "Bạn dùng Spring Boot như thế nào?",
                                "em dùng spring bút",
                                List.of("Spring Boot"),
                                List.of(),
                                List.of(),
                                "")));

        assertEquals("AI_INVALID_JSON", exception.getCode());
    }

    @Test
    void adaptiveGenerationUsesEvidenceSummaryWithoutSendingFullTranscripts() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            respondJson(exchange, chatResponse("""
                    {"questions":[
                      {"questionType":"technical","difficulty":"medium","competencyId":"problem-solving","skillTag":"SQL","question":"Bạn kiểm chứng hiệu quả của index như thế nào?","timeLimitSeconds":180,"expectedEvidence":["đo trước và sau"],"bars":{"level1":"Không đo","level3":"Có đo","level5":"Đo và giải thích trade-off"}},
                      {"questionType":"behavioral","difficulty":"medium","competencyId":"communication","skillTag":null,"question":"Bạn trao đổi thay đổi hiệu năng với nhóm như thế nào?","timeLimitSeconds":180,"expectedEvidence":["hành động và kết quả"],"bars":{"level1":"Mơ hồ","level3":"Có trao đổi","level5":"Có xác nhận kết quả"}}
                    ]}
                    """));
        });
        ShopAiKeyClient client = client(mock(AiInterviewTelemetryService.class), 2_000);
        InterviewSession session = practiceSessionWithProfile();
        session.setEvidenceSummaryJson(Map.of(
                "weakEvidence", List.of("SQL indexing chưa có số đo"),
                "interestingClaims", List.of("đã dùng execution plan")
        ));
        List<InterviewQuestion> questions = List.of(
                technicalQuestion(session, 1, "problem-solving"),
                technicalQuestion(session, 2, "java"),
                technicalQuestion(session, 3, "communication")
        );
        InterviewAnswer answer = new InterviewAnswer();
        answer.setTranscriptText("FULL_TRANSCRIPT_SHOULD_NOT_LEAK");

        List<ShopAiKeyClient.RubricQuestionDraft> drafts =
                client.generateAdaptiveInterviewQuestions(session, questions, List.of(answer));

        assertEquals(2, drafts.size());
        String serializedRequest = requestBody.get().toString();
        assertTrue(serializedRequest.contains("SQL indexing chưa có số đo"));
        assertTrue(!serializedRequest.contains("FULL_TRANSCRIPT_SHOULD_NOT_LEAK"));
    }

    @Test
    void groupedEvaluationReturnsOneBarsRatingForCoreAndFollowUpEvidence() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        InterviewSession session = practiceSessionWithProfile();
        InterviewQuestion question = technicalQuestion(session, 1, "problem-solving");
        startServer(exchange -> {
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            respondJson(exchange, chatResponse("""
                    {
                      "questionRatings":[{
                        "questionId":"%s",
                        "competencyId":"problem-solving",
                        "barsLevel":4,
                        "evidence":["đã phân tích execution plan và đo lại"],
                        "missingEvidence":["chưa nêu chi phí ghi của index"]
                      }],
                      "summary":"Evidence từ core và probe được gộp theo một assessment item.",
                      "strengths":["có kiểm chứng"],
                      "improvements":["nêu trade-off"],
                      "actionPlan":["Trong 7 ngày, luyện giải thích trade-off bằng số đo cho 5 tình huống"]
                    }
                    """.formatted(question.getId())));
        });
        ShopAiKeyClient client = client(mock(AiInterviewTelemetryService.class), 2_000);
        ShopAiKeyClient.GroupedAssessmentEvidence grouped =
                new ShopAiKeyClient.GroupedAssessmentEvidence(
                        question.getId().toString(),
                        question.getCompetencyId(),
                        question.getQuestionType(),
                        question.getContent(),
                        question.getRubric(),
                        List.of(
                                new ShopAiKeyClient.EvidenceTurnDraft(
                                        "CORE_QUESTION", "Tôi kiểm tra execution plan."),
                                new ShopAiKeyClient.EvidenceTurnDraft(
                                        "PROBE", "Tôi đo trước và sau khi thêm index.")
                        )
                );

        ShopAiKeyClient.InterviewEvaluationDraft result =
                client.evaluateGroupedInterview(session, List.of(grouped));

        assertEquals(1, result.questionRatings().size());
        assertEquals(4, result.questionRatings().get(0).barsLevel());
        assertEquals(List.of("Trong 7 ngày, luyện giải thích trade-off bằng số đo cho 5 tình huống"),
                result.actionPlan());
        String serializedRequest = requestBody.get().toString();
        assertTrue(serializedRequest.contains("CORE_QUESTION"));
        assertTrue(serializedRequest.contains("PROBE"));
    }

    private ShopAiKeyClient client(AiInterviewTelemetryService telemetry, int readTimeoutMs) {
        AiInterviewProperties properties = new AiInterviewProperties();
        properties.setShopaikeyApiKey("test-key");
        properties.setShopaikeyBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setShopaikeyModel("gpt-4.1-mini");
        properties.setProviderConnectTimeoutMs(1_000);
        properties.setProviderReadTimeoutMs(readTimeoutMs);
        return new ShopAiKeyClient(
                properties,
                objectMapper,
                RestClient.builder(),
                mock(SystemSettingsService.class),
                telemetry
        );
    }

    private InterviewSession practiceSession() {
        InterviewSession session = new InterviewSession();
        session.setId(UUID.randomUUID());
        session.setSessionType("practice");
        session.setTitle("Java Backend Developer Fresher");
        return session;
    }

    private InterviewSession practiceSessionWithProfile() {
        InterviewSession session = practiceSession();
        session.setEvaluationProfile(Map.of(
                "targetRole", "Java Backend Developer",
                "scoredCompetencyIds", List.of("problem-solving", "java", "communication"),
                "competencies", List.of(
                        Map.of("id", "problem-solving", "name", "Giải quyết vấn đề", "definition", "Phân tích lỗi", "measurementMode", "content"),
                        Map.of("id", "java", "name", "Java", "definition", "Nền tảng Java", "measurementMode", "content"),
                        Map.of("id", "communication", "name", "Giao tiếp", "definition", "Trình bày rõ", "measurementMode", "both")
                )
        ));
        return session;
    }

    private InterviewQuestion technicalQuestion(
            InterviewSession session,
            int orderIndex,
            String competencyId
    ) {
        InterviewQuestion question = new InterviewQuestion();
        question.setId(UUID.randomUUID());
        question.setSession(session);
        question.setOrderIndex(orderIndex);
        question.setQuestionType("technical");
        question.setCompetencyId(competencyId);
        question.setContent("Bạn xử lý một API chậm như thế nào?");
        question.setRubric(Map.of(
                "expectedEvidence", List.of("phân tích", "kiểm chứng"),
                "bars", Map.of("level1", "Mơ hồ", "level3", "Có quy trình", "level5", "Có kiểm chứng")
        ));
        return question;
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> handler.handle(exchange));
        server.start();
    }

    private byte[] successfulInterviewPackageResponse() throws IOException {
        String content = """
                {
                  "evaluationProfile": {
                    "targetRole": "Java Backend Developer Fresher",
                    "seniority": "fresher",
                    "communicationDemand": 3,
                    "scoredCompetencyIds": ["java", "problem-solving", "communication"],
                    "competencies": [
                      {"id":"java","name":"Java","definition":"Nền tảng Java","importance":5,"entryNeedScore":5,"distinguishingValueScore":5,"measurementMode":"content","rationale":"Cốt lõi"},
                      {"id":"problem-solving","name":"Giải quyết vấn đề","definition":"Phân tích và xử lý vấn đề","importance":5,"entryNeedScore":4,"distinguishingValueScore":5,"measurementMode":"content","rationale":"Quan trọng"},
                      {"id":"communication","name":"Giao tiếp","definition":"Trình bày rõ ràng","importance":3,"entryNeedScore":3,"distinguishingValueScore":3,"measurementMode":"both","rationale":"Phối hợp"}
                    ]
                  },
                  "questions": [
                    {"questionType":"general","difficulty":"easy","competencyId":"java","skillTag":"Java","question":"Hãy mô tả một dự án Java bạn đã tham gia?","timeLimitSeconds":180,"expectedEvidence":["vai trò"],"bars":{"level1":"Mơ hồ","level3":"Có ví dụ","level5":"Rõ vai trò và kết quả"}},
                    {"questionType":"technical","difficulty":"medium","competencyId":"problem-solving","skillTag":"Spring Boot","question":"Bạn xử lý một lỗi backend khó như thế nào?","timeLimitSeconds":180,"expectedEvidence":["quy trình"],"bars":{"level1":"Không có cách","level3":"Có quy trình","level5":"Quy trình có kiểm chứng"}},
                    {"questionType":"behavioral","difficulty":"medium","competencyId":"communication","skillTag":null,"question":"Bạn phối hợp với frontend khi yêu cầu API thay đổi như thế nào?","timeLimitSeconds":180,"expectedEvidence":["trao đổi"],"bars":{"level1":"Không phối hợp","level3":"Có trao đổi","level5":"Chủ động xác nhận và theo dõi"}}
                  ]
                }
                """;
        return objectMapper.writeValueAsBytes(objectMapper.createObjectNode()
                .set("choices", objectMapper.createArrayNode().add(
                        objectMapper.createObjectNode().set("message",
                                objectMapper.createObjectNode().put("content", content)))));
    }

    private byte[] chatResponse(String content) throws IOException {
        return objectMapper.writeValueAsBytes(objectMapper.createObjectNode()
                .set("choices", objectMapper.createArrayNode().add(
                        objectMapper.createObjectNode().set("message",
                                 objectMapper.createObjectNode().put("content", content)))));
    }

    private byte[] answerAnalysisResponse(
            String updatedItemSummary,
            List<String> keyClaims
    ) throws IOException {
        var content = objectMapper.createObjectNode();
        content.put("action", "NEXT");
        content.set("keyClaims", objectMapper.valueToTree(keyClaims));
        var coverage = content.putObject("evidenceCoverage");
        coverage.put("accuracy", false);
        coverage.put("reasoning", false);
        coverage.put("tradeOffs", false);
        coverage.put("implementationDetail", false);
        coverage.put("realWorldApplication", false);
        content.set("missingEvidence", objectMapper.valueToTree(List.of("Chưa có evidence cụ thể")));
        content.putNull("followUp");
        content.put("updatedItemSummary", updatedItemSummary);
        var delta = content.putObject("globalEvidenceDelta");
        delta.set("demonstratedCompetencyIds", objectMapper.valueToTree(List.of()));
        delta.set("weakEvidence", objectMapper.valueToTree(List.of("Câu trả lời chưa có evidence")));
        delta.set("interestingClaims", objectMapper.valueToTree(List.of()));
        delta.set("unverifiedClaims", objectMapper.valueToTree(List.of()));
        return chatResponse(content.toString());
    }

    private void respondJson(HttpExchange exchange, byte[] response) throws IOException {
        respondJson(exchange, 200, response);
    }

    private void respondJson(HttpExchange exchange, int status, byte[] response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
