package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.model.entity.CandidateCv;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.CandidateCvRepository;
import com.sjp.recruitment.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiJobSearchCandidateContextBuilderTest {
    private CandidateCvRepository cvRepository;
    private AiJobSearchCandidateContextBuilder builder;
    private CandidateProfile candidate;

    @BeforeEach
    void setUp() {
        cvRepository = mock(CandidateCvRepository.class);
        AiJobSearchProperties properties = new AiJobSearchProperties();
        properties.setPromptVersion("ai-job-search-v1");
        properties.setMaxCvCharacters(12_000);
        builder = new AiJobSearchCandidateContextBuilder(
                cvRepository,
                mock(StorageService.class),
                new ObjectMapper().findAndRegisterModules(),
                properties
        );

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Nguyễn Văn An");
        user.setEmail("an@example.com");
        user.setPhone("0912345678");
        candidate = new CandidateProfile();
        candidate.setId(UUID.randomUUID());
        candidate.setUser(user);
        candidate.setHeadline("Java Backend Developer");
        candidate.setBio("Phát triển hệ thống Spring Boot");
        candidate.setLocation("Hà Nội");
        candidate.setExperienceYears(3);
        candidate.setExperienceLevel("MIDDLE");
        candidate.setSkills(List.of("Spring Boot", "Java"));
        candidate.setEducation(List.of());
        candidate.setWorkExperience(List.of());
        candidate.setProjects(List.of());
        candidate.setCertifications(List.of());
        candidate.setUpdatedAt(LocalDateTime.of(2026, 8, 11, 10, 0));
    }

    @Test
    void removesPiiFromUploadedCvAndCreatesStableHash() {
        CandidateCv cv = new CandidateCv();
        cv.setId(UUID.randomUUID());
        cv.setCandidate(candidate);
        cv.setSourceType("uploaded");
        cv.setParsedText("Nguyễn Văn An · an@example.com · 0912345678 · Java Spring Boot\nNgày sinh: 01/02/2000\nĐịa chỉ: Hà Nội");
        cv.setSnapshot(Map.of());
        cv.setUpdatedAt(LocalDateTime.of(2026, 8, 11, 9, 0));
        when(cvRepository.findFirstByCandidateIdAndDefaultCvTrueAndDeletedAtIsNullOrderByUpdatedAtDesc(candidate.getId()))
                .thenReturn(Optional.of(cv));

        AiJobSearchContext first = builder.build(candidate);
        AiJobSearchContext second = builder.build(candidate);

        assertFalse(first.lowConfidence());
        assertFalse(first.cvText().contains("Nguyễn Văn An"));
        assertFalse(first.cvText().contains("an@example.com"));
        assertFalse(first.cvText().contains("0912345678"));
        assertFalse(first.cvText().contains("01/02/2000"));
        assertFalse(first.cvText().contains("Địa chỉ: Hà Nội"));
        assertEquals(64, first.inputHash().length());
        assertEquals(first.inputHash(), second.inputHash());
        assertFalse(first.providerContext().containsKey("email"));
        assertFalse(first.providerContext().containsKey("phone"));
    }

    @Test
    void profileOnlyIsMarkedLowConfidence() {
        when(cvRepository.findFirstByCandidateIdAndDefaultCvTrueAndDeletedAtIsNullOrderByUpdatedAtDesc(candidate.getId()))
                .thenReturn(Optional.empty());

        AiJobSearchContext context = builder.build(candidate);

        assertTrue(context.lowConfidence());
        assertEquals("", context.cvText());
    }
}
