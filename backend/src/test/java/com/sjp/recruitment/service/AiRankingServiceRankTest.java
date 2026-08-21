package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.model.entity.CandidateCv;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.repository.AiRankingResultRepository;
import com.sjp.recruitment.repository.ApplicationRepository;
import com.sjp.recruitment.repository.CandidateCvRepository;
import com.sjp.recruitment.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRankingServiceRankTest {

    @Mock private AiRankingResultRepository rankingResultRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private CandidateCvRepository candidateCvRepository;
    @Mock private StorageService storageService;
    @Mock private FeatureLimitService featureLimitService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiRankingService aiRankingService;

    @BeforeEach
    void setUp() {
        aiRankingService = new AiRankingService(
                rankingResultRepository,
                applicationRepository,
                candidateCvRepository,
                storageService,
                objectMapper,
                featureLimitService
        );
    }

    @Test
    void rankApplication_skipsWhenApplicationIsMissing() {
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> aiRankingService.rankApplication(applicationId));
        verifyNoInteractions(rankingResultRepository);
    }

    @Test
    void rankApplication_skipsWhenRankingIsDisabled() throws Exception {
        UUID applicationId = UUID.randomUUID();
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setRankingConfig(objectMapper.readTree("{\"enabled\":false}"));
        Application application = new Application();
        application.setId(applicationId);
        application.setJob(job);
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

        assertDoesNotThrow(() -> aiRankingService.rankApplication(applicationId));
        verifyNoInteractions(rankingResultRepository);
    }

    @Test
    void rankApplication_skipsWhenApplicationHasNoCv() {
        UUID applicationId = UUID.randomUUID();
        Job job = new Job();
        job.setId(UUID.randomUUID());
        Application application = new Application();
        application.setId(applicationId);
        application.setJob(job);
        application.setCv(null);
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

        assertDoesNotThrow(() -> aiRankingService.rankApplication(applicationId));
        verifyNoInteractions(rankingResultRepository);
    }

    @Test
    void rankApplication_skipsWhenCvTextCannotBeExtracted() {
        UUID applicationId = UUID.randomUUID();
        Job job = new Job();
        job.setId(UUID.randomUUID());
        CandidateCv cv = new CandidateCv();
        cv.setId(UUID.randomUUID());
        cv.setParsedText(" ");
        cv.setStorageKey(null);
        Application application = new Application();
        application.setId(applicationId);
        application.setJob(job);
        application.setCv(cv);
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

        assertDoesNotThrow(() -> aiRankingService.rankApplication(applicationId));
        verifyNoInteractions(rankingResultRepository);
    }
}
