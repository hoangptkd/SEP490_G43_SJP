package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.entity.AiRankingResult;
import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.model.entity.CandidateCv;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.repository.AiRankingResultRepository;
import com.sjp.recruitment.repository.ApplicationRepository;
import com.sjp.recruitment.repository.CandidateCvRepository;
import com.sjp.recruitment.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRankingService {

    private final AiRankingResultRepository rankingResultRepository;
    private final ApplicationRepository applicationRepository;
    private final CandidateCvRepository candidateCvRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${SHOPAIKEY_API_KEY}")
    private String shopAiKey;

    @Value("${SHOPAIKEY_BASE_URL}")
    private String shopAiBaseUrl;

    @Value("${SHOPAIKEY_MODEL}")
    private String shopAiModel;

    @Transactional
    public void rankApplication(UUID applicationId) {
        try {
            Application application = applicationRepository.findById(applicationId)
                    .orElseThrow(() -> new RuntimeException("Application not found"));

            Job job = application.getJob();
            if (job == null) return;
            
            CandidateCv cv = application.getCv();
            if (cv == null) {
                log.warn("Application {} has no CV, skipping ranking", application.getId());
                return;
            }

            // 1. Extract CV Text using Tika if not already extracted
            String cvText = cv.getParsedText();
            if (cvText == null || cvText.isBlank()) {
                cvText = extractTextFromCv(cv.getStorageKey()); // Assume storageKey is a URL or we need to download it
                if (cvText != null && !cvText.isBlank()) {
                    cv.setParsedText(cvText);
                    candidateCvRepository.save(cv);
                    log.info("Extracted and saved text for CV ID: {}", cv.getId());
                }
            }

            if (cvText == null || cvText.isBlank()) {
                log.warn("Failed to extract text for CV in application {}", application.getId());
                return;
            }

            // 2. Call AI
            Map<String, Object> aiResult = callAiToRank(job, cvText);

            // 3. Calculate overall score
            JsonNode rankingConfig = job.getRankingConfig();
            BigDecimal overallScore = calculateOverallScore(aiResult, rankingConfig);

            // 4. Save result
            AiRankingResult result = rankingResultRepository.findByApplicationId(application.getId())
                    .orElseGet(AiRankingResult::new);
            
            result.setApplication(application);
            result.setMatchScore(overallScore);
            result.setAiSummary((String) aiResult.get("summary"));
            result.setScoreBreakdown(objectMapper.valueToTree(aiResult.get("categoryScores")));
            result.setMissingRequirements(objectMapper.valueToTree(aiResult.get("missingRequirements")));
            result.setModelUsed(shopAiModel);
            result.setRankedAt(LocalDateTime.now());
            
            rankingResultRepository.save(result);

            // Update Application
            application.setAiMatchScore(overallScore != null ? overallScore.intValue() : null);
            application.setAiMatchAnalysis((String) aiResult.get("summary"));
            application.setNeedRerank(false);
            applicationRepository.save(application);

        } catch (Exception e) {
            log.error("Failed to rank application {}", applicationId, e);
        }
    }

    private String extractTextFromCv(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return null;
        try {
            // First check if it's a full URL
            if (storageKey.startsWith("http://") || storageKey.startsWith("https://")) {
                try (InputStream stream = new java.net.URL(storageKey).openStream()) {
                    return new Tika().parseToString(stream);
                }
            } else {
                // It's a local storage key
                org.springframework.core.io.Resource resource = storageService.loadCandidateCv(storageKey);
                try (InputStream stream = resource.getInputStream()) {
                    return new Tika().parseToString(stream);
                }
            }
        } catch (Exception e) {
            log.error("Tika extraction failed for storageKey {}", storageKey, e);
            return null;
        }
    }

    private Map<String, Object> callAiToRank(Job job, String cvText) {
        String prompt = buildPrompt(job, cvText);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + shopAiKey);
        headers.set("Content-Type", "application/json");

        Map<String, Object> body = new HashMap<>();
        body.put("model", shopAiModel);
        body.put("messages", List.of(
            Map.of("role", "system", "content", "You are an expert HR AI assistant. Your task is to evaluate a candidate's CV against a Job Description. Return ONLY a JSON object with this exact format: {\"categoryScores\": {\"skills\": 80, \"experience\": 90, \"projects\": 70, \"education\": 100, \"certificates\": 0}, \"missingRequirements\": [\"AWS cert\", \"5 years Java\"], \"summary\": \"Brief explanation of the score.\"} If any MANDATORY skills or minimum experience are missing from the CV, you MUST list them strictly in the `missingRequirements` array. The `summary` field MUST be written in Vietnamese."),
            Map.of("role", "user", "content", prompt)
        ));
        body.put("response_format", Map.of("type", "json_object"));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(shopAiBaseUrl + "/chat/completions", entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String aiResponseText = root.path("choices").get(0).path("message").path("content").asText();
            
            JsonNode aiJson = objectMapper.readTree(aiResponseText);
            
            Map<String, Object> result = new HashMap<>();
            result.put("categoryScores", objectMapper.convertValue(aiJson.path("categoryScores"), Map.class));
            result.put("missingRequirements", objectMapper.convertValue(aiJson.path("missingRequirements"), List.class));
            result.put("summary", aiJson.path("summary").asText());
            
            return result;
        } catch (Exception e) {
            log.error("AI call failed", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_CALL_FAILED", "Failed to call AI");
        }
    }

    private String buildPrompt(Job job, String cvText) {
        StringBuilder sb = new StringBuilder();
        sb.append("Job Title: ").append(job.getTitle()).append("\n");
        sb.append("Job Description: ").append(job.getDescription()).append("\n");
        sb.append("Job Requirements: ").append(job.getRequirementsText()).append("\n");
        sb.append("Job Skills: ").append(job.getSkills()).append("\n");
        
        com.fasterxml.jackson.databind.JsonNode config = job.getRankingConfig();
        if (config != null && config.has("mandatory")) {
            com.fasterxml.jackson.databind.JsonNode mandatory = config.get("mandatory");
            if (mandatory.has("skills") && mandatory.get("skills").isArray() && mandatory.get("skills").size() > 0) {
                sb.append("MANDATORY SKILLS: ");
                for (com.fasterxml.jackson.databind.JsonNode skill : mandatory.get("skills")) {
                    sb.append(skill.asText()).append(", ");
                }
                sb.append("\n");
            }
            if (mandatory.has("min_experience_years") && !mandatory.get("min_experience_years").isNull()) {
                sb.append("MANDATORY MINIMUM EXPERIENCE: ").append(mandatory.get("min_experience_years").asDouble()).append(" years\n");
            }
        }

        sb.append("\n--- CANDIDATE CV ---\n");
        sb.append(cvText);
        return sb.toString();
    }

    private BigDecimal calculateOverallScore(Map<String, Object> aiResult, JsonNode config) {
        if (config == null || !config.has("weights")) return BigDecimal.ZERO;
        
        Map<String, Integer> categoryScores = (Map<String, Integer>) aiResult.get("categoryScores");
        JsonNode weights = config.path("weights");
        
        double totalScore = 0;
        double totalWeight = 0;
        
        if (categoryScores != null) {
            for (Map.Entry<String, Integer> entry : categoryScores.entrySet()) {
                String category = entry.getKey();
                int score = entry.getValue() != null ? entry.getValue() : 0;
                
                if (weights.has(category)) {
                    double weight = weights.get(category).asDouble();
                    totalScore += (score * weight);
                    totalWeight += weight;
                }
            }
        }
        
        if (totalWeight == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(totalScore / totalWeight);
    }
}
