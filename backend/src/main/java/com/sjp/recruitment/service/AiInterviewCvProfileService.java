package com.sjp.recruitment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.AiInterviewCvProfileResponse;
import com.sjp.recruitment.model.entity.AiInterviewCvProfile;
import com.sjp.recruitment.model.entity.CandidateCv;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.repository.AiInterviewCvProfileRepository;
import com.sjp.recruitment.service.ai.AiInterviewRateLimiter;
import com.sjp.recruitment.service.ai.AiJobSearchCandidateContextBuilder;
import com.sjp.recruitment.service.ai.AiJobSearchContext;
import com.sjp.recruitment.service.ai.AiProviderException;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiInterviewCvProfileService {

    private final AiInterviewProperties properties;
    private final CandidateService candidateService;
    private final AiJobSearchCandidateContextBuilder contextBuilder;
    private final AiInterviewCvProfileRepository profileRepository;
    private final ShopAiKeyClient shopAiKeyClient;
    private final AiInterviewRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public AiInterviewCvProfileResponse analyze(String cvId) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "cv-profile");
        UUID parsedCvId = parseUuid(cvId);
        AiJobSearchContext context = contextBuilder.build(candidate, parsedCvId);
        CandidateCv cv = context.defaultCv();
        if (context.cvText() == null || context.cvText().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CV_TEXT_REQUIRED",
                    "CV chưa có nội dung có thể phân tích. Vui lòng cập nhật hoặc tải lại CV.");
        }

        String contentHash = sha256(context.cvText());
        String promptVersion = properties.getCvProfilePromptVersion();
        String model = properties.getShopaikeyModel();
        AiInterviewCvProfile cached = profileRepository
                .findFirstByCandidateIdAndCvIdAndContentHashAndPromptVersionAndModelUsedOrderByCreatedAtDesc(
                        candidate.getId(), cv.getId(), contentHash, promptVersion, model)
                .orElse(null);
        if (cached != null) {
            return toResponse(cached, cv, true);
        }

        ShopAiKeyClient.CvInterviewProfileDraft draft;
        try {
            draft = shopAiKeyClient.analyzeCvInterviewProfile(Map.of(
                    "cvId", cv.getId().toString(),
                    "cvTitle", cv.getTitle(),
                    "cvContent", context.cvText()
            ));
        } catch (AiProviderException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, exception.getCode(),
                    "AI không thể phân tích CV: " + exception.getMessage());
        }

        AiInterviewCvProfile profile = new AiInterviewCvProfile();
        profile.setCandidate(candidate);
        profile.setCv(cv);
        profile.setContentHash(contentHash);
        profile.setPromptVersion(promptVersion);
        profile.setModelUsed(model);
        profile.setProfileJson(toProfileJson(draft));
        return toResponse(profileRepository.save(profile), cv, false);
    }

    private Map<String, Object> toProfileJson(ShopAiKeyClient.CvInterviewProfileDraft draft) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("summary", draft.summary());
        json.put("experienceLevel", draft.experienceLevel());
        json.put("skills", draft.skills());
        json.put("suggestedRoles", objectMapper.convertValue(
                draft.suggestedRoles(), new TypeReference<List<Map<String, Object>>>() {}));
        json.put("evidenceClaims", objectMapper.convertValue(
                draft.evidenceClaims(), new TypeReference<List<Map<String, Object>>>() {}));
        return json;
    }

    private AiInterviewCvProfileResponse toResponse(AiInterviewCvProfile profile,
                                                     CandidateCv selectedCv,
                                                     boolean cached) {
        Map<String, Object> json = profile.getProfileJson();
        List<String> skills = objectMapper.convertValue(
                json.getOrDefault("skills", List.of()), new TypeReference<List<String>>() {});
        List<AiInterviewCvProfileResponse.RoleSuggestion> roles = objectMapper.convertValue(
                json.getOrDefault("suggestedRoles", List.of()),
                new TypeReference<List<AiInterviewCvProfileResponse.RoleSuggestion>>() {});
        List<AiInterviewCvProfileResponse.EvidenceClaim> claims = objectMapper.convertValue(
                json.getOrDefault("evidenceClaims", List.of()),
                new TypeReference<List<AiInterviewCvProfileResponse.EvidenceClaim>>() {});
        return new AiInterviewCvProfileResponse(
                profile.getId().toString(),
                selectedCv.getId().toString(),
                selectedCv.getTitle(),
                profile.getContentHash(),
                String.valueOf(json.getOrDefault("summary", "")),
                String.valueOf(json.getOrDefault("experienceLevel", "fresher")),
                List.copyOf(skills),
                List.copyOf(roles),
                List.copyOf(claims),
                profile.getPromptVersion(),
                cached
        );
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CV_ID_INVALID", "Mã CV không hợp lệ.");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
