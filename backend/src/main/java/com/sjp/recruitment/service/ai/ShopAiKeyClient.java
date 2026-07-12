package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.entity.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShopAiKeyClient {

    private final AiInterviewProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public QuestionDraft generateQuestion(InterviewSession session,
                                          CandidateProfile candidate,
                                          List<InterviewQuestion> previousQuestions,
                                          List<InterviewAnswer> previousAnswers) {
        String prompt = """
                Tao 1 cau hoi phong van tiep theo bang tieng Viet.
                Chi tra JSON hop le, khong markdown.
                Yeu cau bat buoc:
                - Cau hoi toi da 220 ky tu.
                - Chi hoi 1 y chinh, khong yeu cau liet ke endpoint/schema/test day du.
                - Phu hop tra loi bang giong noi trong 2-3 phut.
                - Khong tao bai tap thiet ke he thong qua lon.
                JSON schema:
                {
                  "questionType": "behavioral|technical|situational|general",
                  "difficulty": "easy|medium|hard",
                  "skillTag": "string or null",
                  "question": "string",
                  "timeLimitSeconds": 180
                }
                Context:
                %s
                Previous:
                %s
                """.formatted(buildContext(session, candidate), buildHistory(previousQuestions, previousAnswers));
        JsonNode json = callJson(prompt, 700, 0.3);
        return new QuestionDraft(
                oneOf(json.path("questionType").asText("general"), List.of("behavioral", "technical", "situational", "general"), "general"),
                oneOf(json.path("difficulty").asText("medium"), List.of("easy", "medium", "hard"), "medium"),
                textOrNull(json.path("skillTag")),
                conciseQuestion(requireText(json, "question")),
                clampInt(json.path("timeLimitSeconds").asInt(properties.getAudioMaxSeconds()), 30, properties.getAudioMaxSeconds())
        );
    }

    public AnswerFeedbackDraft evaluateAnswer(InterviewSession session,
                                              InterviewQuestion question,
                                              String transcript) {
        String prompt = """
                Ban la AI coach phong van. Danh gia cau tra loi bang tieng Viet.
                Day chi la feedback luyen tap, khong phai quyet dinh tuyen dung.
                Chi tra JSON hop le, khong markdown.
                JSON schema:
                {
                  "score": 0,
                  "feedback": "string",
                  "strengths": ["string"],
                  "weaknesses": ["string"],
                  "suggestions": ["string"]
                }
                Cau hoi: %s
                Cau tra loi transcript: %s
                Context: %s
                """.formatted(question.getContent(), transcript, buildSessionContext(session));
        JsonNode json = callJson(prompt, 900, 0.2);
        return new AnswerFeedbackDraft(
                clampScore(json.path("score").decimalValue()),
                requireText(json, "feedback"),
                stringList(json.path("strengths")),
                stringList(json.path("weaknesses")),
                stringList(json.path("suggestions"))
        );
    }

    public SessionSummaryDraft summarizeSession(InterviewSession session,
                                                List<InterviewQuestion> questions,
                                                List<InterviewAnswer> answers,
                                                BigDecimal calculatedScore) {
        String prompt = """
                Tong ket buoi phong van luyen tap bang tieng Viet.
                Chi tra JSON hop le, khong markdown.
                JSON schema:
                {
                  "summary": "string",
                  "strengths": ["string"],
                  "weaknesses": ["string"],
                  "improvementPlan": ["string"]
                }
                Overall score da tinh theo trung binh cau hoi: %s
                Context: %s
                Transcript history: %s
                """.formatted(calculatedScore, buildSessionContext(session), buildHistory(questions, answers));
        JsonNode json = callJson(prompt, 1000, 0.2);
        return new SessionSummaryDraft(
                calculatedScore,
                requireText(json, "summary"),
                stringList(json.path("strengths")),
                stringList(json.path("weaknesses")),
                stringList(json.path("improvementPlan"))
        );
    }

    @SuppressWarnings("unchecked")
    private JsonNode callJson(String userPrompt, int maxTokens, double temperature) {
        try {
            RestClient client = buildClient();
            Map<String, Object> body = Map.of(
                    "model", properties.getShopaikeyModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", "You return strict JSON only."),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "max_tokens", maxTokens,
                    "temperature", temperature
            );
            Map<String, Object> response = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.getShopaikeyApiKey())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            List<Map<String, Object>> choices = response == null ? List.of() : (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AiProviderException("AI_EMPTY_RESPONSE", "ShopAIKey khong tra ket qua");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            Object content = message == null ? null : message.get("content");
            if (!(content instanceof String value) || value.isBlank()) {
                throw new AiProviderException("AI_EMPTY_CONTENT", "ShopAIKey khong tra noi dung");
            }
            return objectMapper.readTree(extractJson(value));
        } catch (JsonProcessingException | RuntimeException exception) {
            if (exception instanceof AiProviderException aiProviderException) {
                throw aiProviderException;
            }
            throw new AiProviderException("AI_PROVIDER_FAILED", "He thong chua xu ly duoc cau tra loi nay, vui long thu lai.");
        }
    }

    private String buildContext(InterviewSession session, CandidateProfile candidate) {
        return "Session=" + buildSessionContext(session)
                + "; Candidate skills=" + candidate.getSkills()
                + "; Candidate bio=" + safe(candidate.getBio())
                + "; Candidate headline=" + safe(candidate.getHeadline());
    }

    private String buildSessionContext(InterviewSession session) {
        Job job = session.getJob();
        String jobText = job == null ? "none" : job.getTitle() + " / " + job.getRequirementsText();
        return "type=" + ("job_based".equals(session.getSessionType()) ? "application" : "practice")
                + "; title=" + session.getTitle()
                + "; job=" + jobText
                + "; practiceContext=" + session.getPracticeContext();
    }

    private String buildHistory(List<InterviewQuestion> questions, List<InterviewAnswer> answers) {
        StringBuilder builder = new StringBuilder();
        for (InterviewQuestion question : questions) {
            builder.append("Q").append(question.getOrderIndex()).append(": ").append(question.getContent()).append("\n");
            answers.stream()
                    .filter(answer -> answer.getQuestionId().equals(question.getId()))
                    .findFirst()
                    .ifPresent(answer -> builder.append("A: ")
                            .append(answer.isSkipped() ? "[SKIPPED]" : safe(answer.getTranscriptText()))
                            .append("\n"));
        }
        return builder.toString();
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiProviderException("AI_INVALID_JSON", "AI khong tra JSON hop le");
        }
        return content.substring(start, end + 1);
    }

    private String requireText(JsonNode json, String field) {
        String value = json.path(field).asText("");
        if (value.isBlank()) {
            throw new AiProviderException("AI_MISSING_FIELD", "AI thieu truong " + field);
        }
        return value;
    }

    private String conciseQuestion(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 260 || normalized.split("\\?").length > 2) {
            throw new AiProviderException("AI_QUESTION_TOO_LONG", "AI tao cau hoi qua dai");
        }
        return normalized;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("");
        return value.isBlank() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                String value = item.asText("");
                if (!value.isBlank()) {
                    values.add(value);
                }
            });
        }
        return values;
    }

    private BigDecimal clampScore(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO;
        }
        return score.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String oneOf(String value, List<String> allowed, String fallback) {
        return allowed.contains(value) ? value : fallback;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://api.shopaikey.com/v1";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private RestClient buildClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getProviderConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getProviderReadTimeoutMs()));
        return restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(trimTrailingSlash(properties.getShopaikeyBaseUrl()))
                .build();
    }

    public record QuestionDraft(String questionType, String difficulty, String skillTag, String question, Integer timeLimitSeconds) {
    }

    public record AnswerFeedbackDraft(BigDecimal score, String feedback, List<String> strengths, List<String> weaknesses, List<String> suggestions) {
    }

    public record SessionSummaryDraft(BigDecimal overallScore, String summary, List<String> strengths, List<String> weaknesses, List<String> improvementPlan) {
    }
}
