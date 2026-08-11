package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
@RequiredArgsConstructor
public class ShopAiKeyJobSearchClient {
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final AiJobSearchProperties properties;

    @SuppressWarnings("unchecked")
    public JsonNode rank(AiJobSearchContext context, List<AiJobSearchCandidateSelector.SelectedJob> candidates) {
        try {
            Map<String, Object> body = Map.of(
                    "model", properties.getShopaikeyModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt()),
                            Map.of("role", "user", "content", userPrompt(context, candidates))
                    ),
                    "max_tokens", 2500,
                    "temperature", 0.1
            );
            Map<String, Object> response = client().post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.getShopaikeyApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            List<Map<String, Object>> choices = response == null
                    ? List.of()
                    : (List<Map<String, Object>>) response.getOrDefault("choices", List.of());
            if (choices.isEmpty()) {
                throw providerFailure("AI_JOB_SEARCH_EMPTY_RESPONSE");
            }
            Object messageValue = choices.get(0).get("message");
            if (!(messageValue instanceof Map<?, ?> message) || !(message.get("content") instanceof String content)
                    || content.isBlank()) {
                throw providerFailure("AI_JOB_SEARCH_EMPTY_CONTENT");
            }
            try {
                return objectMapper.readTree(extractJson(content));
            } catch (AiJobSearchValidationException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new AiJobSearchValidationException("Provider response is not valid JSON");
            }
        } catch (AiJobSearchValidationException exception) {
            throw exception;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw providerFailure("AI_JOB_SEARCH_PROVIDER_FAILED");
        }
    }

    private String systemPrompt() {
        return """
                Bạn là hệ thống xếp hạng công việc cho ứng viên, không phải hệ thống ra quyết định tuyển dụng.
                Chỉ chọn jobId có trong danh sách đầu vào. Không suy đoán thông tin không có trong dữ liệu.
                Trả về JSON duy nhất theo dạng {"items":[{"jobId":"uuid","matchScore":0,"matchedSkills":[],"missingSkills":[],"reason":"..."}]}.
                Điểm nằm trong 0-100. Lý do viết bằng tiếng Việt có dấu, ngắn gọn và dựa trên dữ liệu đầu vào.
                Không trả markdown, giải thích ngoài JSON hoặc dữ liệu nhận dạng cá nhân.
                """;
    }

    private String userPrompt(AiJobSearchContext context, List<AiJobSearchCandidateSelector.SelectedJob> candidates) throws Exception {
        List<Map<String, Object>> jobs = candidates.stream()
                .limit(properties.getMaxPromptJobs())
                .map(item -> {
                    var job = item.job();
                    Map<String, Object> compact = new LinkedHashMap<>();
                    compact.put("jobId", job.getId().toString());
                    compact.put("title", safe(job.getTitle()));
                    compact.put("company", job.getCompany() == null ? "" : safe(job.getCompany().getName()));
                    compact.put("description", truncate(job.getDescription(), 1800));
                    compact.put("requirements", truncate(job.getRequirementsText(), 1800));
                    compact.put("skills", job.getSkills());
                    compact.put("location", safe(job.getLocation()));
                    compact.put("experienceLevel", safe(job.getExperienceLevel()));
                    compact.put("jobType", safe(job.getJobType()));
                    compact.put("workMode", safe(job.getWorkMode()));
                    compact.put("salaryMin", job.getSalaryMin());
                    compact.put("salaryMax", job.getSalaryMax());
                    return compact;
                })
                .toList();
        return "CandidateContext=" + objectMapper.writeValueAsString(context.providerContext())
                + "\nCandidateJobs=" + objectMapper.writeValueAsString(jobs)
                + "\nHãy xếp hạng tối đa " + properties.getMaxResults() + " công việc phù hợp nhất.";
    }

    private RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getProviderConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getProviderReadTimeoutMs()));
        return restClientBuilder.requestFactory(factory)
                .baseUrl(trimTrailingSlash(properties.getShopaikeyBaseUrl()))
                .build();
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiJobSearchValidationException("Provider response is not a JSON object");
        }
        return content.substring(start, end + 1);
    }

    private ApiException providerFailure(String code) {
        return new ApiException(BAD_GATEWAY, code,
                "AI chưa thể tạo gợi ý việc làm lúc này. Vui lòng thử lại sau.");
    }

    private String truncate(String value, int max) {
        String safe = safe(value).replaceAll("\\s+", " ");
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://api.shopaikey.com/v1";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
