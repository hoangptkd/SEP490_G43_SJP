package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.entity.CandidateCv;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.repository.CandidateCvRepository;
import com.sjp.recruitment.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiJobSearchCandidateContextBuilder {
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?84|0)[\\s.-]?(?:\\d[\\s.-]?){8,10}(?!\\d)");
    private static final Pattern DATE_OF_BIRTH = Pattern.compile(
            "(?imu)(ngày\\s*sinh|date\\s*of\\s*birth|dob)\\s*[:\\-]?\\s*\\d{1,2}[\\s./-]\\d{1,2}[\\s./-]\\d{2,4}");
    private static final Pattern ADDRESS = Pattern.compile(
            "(?imu)^(địa\\s*chỉ|address)\\s*[:\\-]\\s*[^\\r\\n]{1,240}");
    private static final Set<String> PRIVATE_KEYS = Set.of(
            "fullname", "firstname", "lastname", "email", "phone", "phonenumber",
            "dateofbirth", "dob", "birthday", "address", "avatar", "avatarurl", "photo",
            "hoten", "hovaten", "tenungvien", "sodienthoai", "ngaysinh", "diachi"
    );

    private final CandidateCvRepository candidateCvRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;
    private final AiJobSearchProperties properties;

    @Transactional
    public AiJobSearchContext build(CandidateProfile candidate) {
        if (candidate == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_JOB_SEARCH_PROFILE_REQUIRED",
                    "Vui lòng hoàn thiện hồ sơ ứng viên trước khi sử dụng tìm việc bằng AI.");
        }
        CandidateCv cv = candidateCvRepository
                .findFirstByCandidateIdAndDefaultCvTrueAndDeletedAtIsNullOrderByUpdatedAtDesc(candidate.getId())
                .orElse(null);
        List<String> skills = candidate.getSkills().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.replaceAll("\\s+", " "))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        String cvText = cv == null ? "" : normalizedCvText(cv, candidate);
        Map<String, Object> providerContext = new LinkedHashMap<>();
        providerContext.put("headline", safe(candidate.getHeadline()));
        providerContext.put("professionalSummary", safe(candidate.getBio()));
        providerContext.put("location", safe(candidate.getLocation()));
        providerContext.put("experienceYears", candidate.getExperienceYears());
        providerContext.put("experienceLevel", safe(candidate.getExperienceLevel()));
        providerContext.put("desiredJobTitles", safeList(candidate.getDesiredJobTitles()));
        providerContext.put("expectedSalary", candidate.getExpectedSalary());
        providerContext.put("preferredLocations", safeList(candidate.getPreferredLocations()));
        providerContext.put("willingToRelocate", candidate.isWillingToRelocate());
        providerContext.put("skills", skills);
        providerContext.put("education", safeList(candidate.getEducation()));
        providerContext.put("workExperience", safeList(candidate.getWorkExperience()));
        providerContext.put("projects", safeList(candidate.getProjects()));
        providerContext.put("certifications", safeList(candidate.getCertifications()));
        providerContext.put("cvContent", cvText);

        Map<String, Object> hashInput = new TreeMap<>();
        hashInput.putAll(providerContext);
        hashInput.put("defaultCvId", cv == null ? null : cv.getId().toString());
        hashInput.put("defaultCvType", cv == null ? null : cvType(cv));
        hashInput.put("promptVersion", properties.getPromptVersion());

        return new AiJobSearchContext(
                candidate,
                cv,
                sha256(toJson(hashInput)),
                cv == null,
                skills,
                safe(candidate.getHeadline()),
                safe(candidate.getBio()),
                safe(candidate.getLocation()),
                candidate.getExperienceYears(),
                safe(candidate.getExperienceLevel()),
                safeStringList(candidate.getDesiredJobTitles()),
                candidate.getExpectedSalary(),
                safeStringList(candidate.getPreferredLocations()),
                candidate.isWillingToRelocate(),
                safeList(candidate.getEducation()),
                safeList(candidate.getWorkExperience()),
                safeList(candidate.getProjects()),
                safeList(candidate.getCertifications()),
                cvText,
                Collections.unmodifiableMap(providerContext)
        );
    }

    private List<String> safeStringList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String normalizedCvText(CandidateCv cv, CandidateProfile candidate) {
        String sourceType = safe(cv.getSourceType()).toLowerCase(Locale.ROOT);
        String raw;
        if ("builder".equals(sourceType)) {
            raw = toJson(removePrivateFields(cv.getSnapshot()));
        } else {
            raw = safe(cv.getParsedText());
            if (raw.isBlank()) {
                raw = extractUploadedCv(cv);
                if (!raw.isBlank()) {
                    cv.setParsedText(normalizeWhitespace(raw));
                    cv.setParseStatus("parsed");
                    candidateCvRepository.save(cv);
                }
            }
        }
        String sanitized = EMAIL.matcher(raw).replaceAll("[EMAIL_REMOVED]");
        sanitized = PHONE.matcher(sanitized).replaceAll("[PHONE_REMOVED]");
        sanitized = DATE_OF_BIRTH.matcher(sanitized).replaceAll("$1: [DOB_REMOVED]");
        sanitized = ADDRESS.matcher(sanitized).replaceAll("$1: [ADDRESS_REMOVED]");
        String fullName = candidate.getFullName();
        if (fullName != null && !fullName.isBlank()) {
            sanitized = sanitized.replaceAll("(?i)" + Pattern.quote(fullName.trim()), "[NAME_REMOVED]");
        }
        sanitized = normalizeWhitespace(sanitized);
        return sanitized.length() <= properties.getMaxCvCharacters()
                ? sanitized
                : sanitized.substring(0, properties.getMaxCvCharacters());
    }

    private String extractUploadedCv(CandidateCv cv) {
        if (cv.getStorageKey() == null || cv.getStorageKey().isBlank()) {
            return "";
        }
        try {
            try (InputStream stream = storageService.loadCandidateCv(cv.getStorageKey()).getInputStream()) {
                return new Tika().parseToString(stream);
            }
        } catch (Exception exception) {
            log.warn("Không thể trích xuất CV cho AI Job Search, cvId={}", cv.getId());
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private Object removePrivateFields(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> cleaned = new TreeMap<>();
            map.forEach((key, child) -> {
                String textKey = String.valueOf(key);
                String normalizedKey = Normalizer.normalize(textKey, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .replaceAll("[^A-Za-z]", "")
                        .toLowerCase(Locale.ROOT);
                if (!PRIVATE_KEYS.contains(normalizedKey)) {
                    cleaned.put(textKey, removePrivateFields(child));
                }
            });
            return cleaned;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::removePrivateFields).toList();
        }
        return value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_JOB_SEARCH_CONTEXT_FAILED",
                    "Không thể chuẩn bị dữ liệu nghề nghiệp cho AI.");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String cvType(CandidateCv cv) {
        return "builder".equalsIgnoreCase(cv.getSourceType()) ? "BUILDER" : "UPLOADED";
    }

    private List<?> safeList(List<?> value) {
        return value == null ? List.of() : value;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeWhitespace(String value) {
        return safe(value).replaceAll("[\\p{Z}\\s]+", " ").trim();
    }
}
