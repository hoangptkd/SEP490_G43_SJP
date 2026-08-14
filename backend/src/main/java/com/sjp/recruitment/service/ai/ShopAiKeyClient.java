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
import com.sjp.recruitment.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShopAiKeyClient {

    private final AiInterviewProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final SystemSettingsService systemSettingsService;

    public QuestionDraft generateQuestion(InterviewSession session,
                                          CandidateProfile candidate,
                                          List<InterviewQuestion> previousQuestions,
                                          List<InterviewAnswer> previousAnswers) {
        String prompt = withSystemPrompt("""
                Tạo 1 câu hỏi phỏng vấn tiếp theo bằng tiếng Việt có dấu.
                Chỉ trả JSON hợp lệ, không markdown.
                Yêu cầu bắt buộc:
                - Câu hỏi tối đa 220 ký tự.
                - Chỉ hỏi 1 ý chính, không yêu cầu liệt kê endpoint/schema/test đầy đủ.
                - Phù hợp trả lời bằng giọng nói trong 2-3 phút.
                - Không tạo bài tập thiết kế hệ thống quá lớn.
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
                """.formatted(buildContext(session, candidate), buildHistory(previousQuestions, previousAnswers)));
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
        String prompt = withFeedbackPrompt("""
                Bạn là AI coach phỏng vấn. Đánh giá câu trả lời bằng tiếng Việt có dấu.
                Đây chỉ là feedback luyện tập, không phải quyết định tuyển dụng.
                Chỉ trả JSON hợp lệ, không markdown.
                Score bắt buộc là thang 0-100, không dùng thang 0-10.
                Tự chấm trực tiếp trên tổng 100 điểm theo rubric:
                - Đúng trọng tâm và chính xác: tối đa 40 điểm.
                - Chiều sâu và mức độ đầy đủ: tối đa 30 điểm.
                - Cấu trúc và diễn đạt rõ ràng: tối đa 20 điểm.
                - Ví dụ hoặc bằng chứng cụ thể: tối đa 10 điểm.
                Trường score là tổng điểm cuối cùng trên thang 0-100.
                JSON schema:
                {
                  "score": 0-100,
                  "feedback": "string",
                  "strengths": ["string"],
                  "weaknesses": ["string"],
                  "suggestions": ["string"]
                }
                Câu hỏi: %s
                Câu trả lời transcript: %s
                Context: %s
                """.formatted(question.getContent(), transcript, buildSessionContext(session)));
        JsonNode json = callJson(prompt, 900, 0.2);
        return new AnswerFeedbackDraft(
                requireScore(json),
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
                Tổng kết buổi phỏng vấn luyện tập bằng tiếng Việt có dấu.
                Chỉ trả JSON hợp lệ, không markdown.
                JSON schema:
                {
                  "summary": "string",
                  "strengths": ["string"],
                  "weaknesses": ["string"],
                  "improvementPlan": ["string"]
                }
                Overall score đã tính theo trung bình câu hỏi: %s
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

    public void streamSpeech(String input, OutputStream outputStream) {
        if (input == null || input.isBlank()) {
            throw new AiProviderException("TTS_INPUT_REQUIRED", "Nội dung đọc không được để trống");
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", properties.getShopaikeyTtsModel(),
                    "voice", properties.getShopaikeyTtsVoice(),
                    "input", input.trim(),
                    "instructions", properties.getShopaikeyTtsInstructions(),
                    "response_format", properties.getShopaikeyTtsFormat()
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(properties.getShopaikeyBaseUrl()) + "/audio/speech"))
                    .timeout(Duration.ofMillis(Math.max(properties.getProviderReadTimeoutMs(), 30_000)))
                    .header("Authorization", "Bearer " + properties.getShopaikeyApiKey())
                    .header("Accept", speechContentType())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(properties.getProviderConnectTimeoutMs()))
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (InputStream errorStream = response.body()) {
                    String errorBody = new String(errorStream.readNBytes(1_000));
                    throw new AiProviderException("TTS_PROVIDER_FAILED", "ShopAIKey TTS không phản hồi thành công: " + response.statusCode() + " " + errorBody);
                }
            }
            try (InputStream inputStream = response.body()) {
                inputStream.transferTo(outputStream);
                outputStream.flush();
            }
        } catch (IOException exception) {
            throw new AiProviderException("TTS_PROVIDER_FAILED", "Không thể tạo audio tiếng Việt từ ShopAIKey TTS");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("TTS_PROVIDER_INTERRUPTED", "Tác vụ tạo audio đã bị gián đoạn");
        }
    }

    public String speechContentType() {
        return switch (properties.getShopaikeyTtsFormat().toLowerCase()) {
            case "wav" -> "audio/wav";
            case "opus" -> "audio/ogg";
            case "aac" -> "audio/aac";
            case "flac" -> "audio/flac";
            case "pcm" -> "audio/L16";
            default -> "audio/mpeg";
        };
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
                throw new AiProviderException("AI_EMPTY_RESPONSE", "ShopAIKey không trả kết quả");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            Object content = message == null ? null : message.get("content");
            if (!(content instanceof String value) || value.isBlank()) {
                throw new AiProviderException("AI_EMPTY_CONTENT", "ShopAIKey không trả nội dung");
            }
            return objectMapper.readTree(extractJson(value));
        } catch (JsonProcessingException | RuntimeException exception) {
            if (exception instanceof AiProviderException aiProviderException) {
                throw aiProviderException;
            }
            throw new AiProviderException("AI_PROVIDER_FAILED", "Hệ thống chưa xử lý được câu trả lời này, vui lòng thử lại.");
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

    private String withSystemPrompt(String prompt) {
        String custom = systemSettingsService.getString(SystemSettingsService.AI_SYSTEM_PROMPT, "");
        if (!StringUtils.hasText(custom)) {
            return prompt;
        }
        return "Hướng dẫn hệ thống từ admin:\n" + custom.trim() + "\n\n" + prompt;
    }

    private String withFeedbackPrompt(String prompt) {
        String custom = systemSettingsService.getString(SystemSettingsService.AI_FEEDBACK_PROMPT, "");
        if (!StringUtils.hasText(custom)) {
            return prompt;
        }
        return "Hướng dẫn đánh giá từ admin:\n" + custom.trim() + "\n\n" + prompt;
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiProviderException("AI_INVALID_JSON", "AI không trả JSON hợp lệ");
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
            throw new AiProviderException("AI_QUESTION_TOO_LONG", "AI tạo câu hỏi quá dài");
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

    private BigDecimal requireScore(JsonNode json) {
        JsonNode scoreNode = json == null ? null : json.get("score");
        if (scoreNode == null || !scoreNode.isNumber()) {
            throw new AiProviderException(
                    "AI_INVALID_SCORE",
                    "AI không trả về điểm số hợp lệ trên thang 0-100."
            );
        }
        BigDecimal score = scoreNode.decimalValue();
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new AiProviderException(
                    "AI_INVALID_SCORE",
                    "AI trả về điểm nằm ngoài thang 0-100."
            );
        }
        return score;
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
