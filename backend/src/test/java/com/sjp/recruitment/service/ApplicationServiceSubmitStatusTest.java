package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.ApplicationSubmitRequest;
import com.sjp.recruitment.model.dto.response.ApplicationResponse;
import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.model.entity.CandidateCv;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.ApplicationRepository;
import com.sjp.recruitment.repository.ApplicationStatusHistoryRepository;
import com.sjp.recruitment.repository.CandidateCvRepository;
import com.sjp.recruitment.repository.CvVersionRepository;
import com.sjp.recruitment.repository.EmployerRepository;
import com.sjp.recruitment.repository.InterviewScheduleRepository;
import com.sjp.recruitment.repository.JobOfferRepository;
import com.sjp.recruitment.repository.JobRepository;
import com.sjp.recruitment.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceSubmitStatusTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private JobRepository jobRepository;
    @Mock private CandidateCvRepository candidateCvRepository;
    @Mock private CvVersionRepository cvVersionRepository;
    @Mock private ApplicationStatusHistoryRepository historyRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private InterviewScheduleRepository interviewScheduleRepository;
    @Mock private JobOfferRepository jobOfferRepository;
    @Mock private CandidateService candidateService;
    @Mock private JobService jobService;
    @Mock private DtoMapper dtoMapper;
    @Mock private FeatureLimitService featureLimitService;
    @Mock private EmployerRepository employerRepository;
    @Mock private AiRankingService aiRankingService;
    @Mock private CandidateRealtimeEventPublisher realtimeEventPublisher;

    @InjectMocks private ApplicationService applicationService;

    @Test
    void submit_rejectsIncompleteProfile() {
        CandidateProfile candidate = candidate();
        when(candidateService.getCurrentCandidateProfile()).thenReturn(candidate);
        doNothing().when(candidateService).requireCandidate(candidate.getUser());
        doNothing().when(featureLimitService).requireApplication(candidate.getUser());
        when(candidateService.isApplyReady(candidate)).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> applicationService.submit(request(UUID.randomUUID(), UUID.randomUUID())));
        assertEquals("PROFILE_INCOMPLETE", ex.getCode());
    }

    @Test
    void submit_rejectsMissingJob() {
        CandidateProfile candidate = readyCandidate();
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> applicationService.submit(request(jobId, UUID.randomUUID())));
        assertEquals("JOB_NOT_FOUND", ex.getCode());
    }

    @Test
    void submit_rejectsClosedJob() {
        CandidateProfile candidate = readyCandidate();
        Job job = publishedJob();
        job.setStatus("closed");
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        ApiException ex = assertThrows(ApiException.class, () -> applicationService.submit(request(job.getId(), UUID.randomUUID())));
        assertEquals("JOB_NOT_OPEN", ex.getCode());
    }

    @Test
    void submit_rejectsExpiredJob() {
        CandidateProfile candidate = readyCandidate();
        Job job = publishedJob();
        job.setDeadline(LocalDate.now().minusDays(1));
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        ApiException ex = assertThrows(ApiException.class, () -> applicationService.submit(request(job.getId(), UUID.randomUUID())));
        assertEquals("JOB_NOT_OPEN", ex.getCode());
    }

    @Test
    void submit_rejectsDuplicateApplication() {
        CandidateProfile candidate = readyCandidate();
        Job job = publishedJob();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(applicationRepository.existsByCandidateIdAndJobId(candidate.getId(), job.getId())).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> applicationService.submit(request(job.getId(), UUID.randomUUID())));
        assertEquals("APPLICATION_DUPLICATED", ex.getCode());
    }

    @Test
    void submit_requiresExactlyOneCvReference() {
        CandidateProfile candidate = readyCandidate();
        Job job = publishedJob();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(applicationRepository.existsByCandidateIdAndJobId(candidate.getId(), job.getId())).thenReturn(false);

        ApiException missing = assertThrows(ApiException.class,
                () -> applicationService.submit(new ApplicationSubmitRequest(job.getId().toString(), null, null, "Hanoi", null)));
        assertEquals("CV_REFERENCE_INVALID", missing.getCode());
    }

    @Test
    void submit_savesApplicationAsSubmitted() {
        CandidateProfile candidate = readyCandidate();
        Job job = publishedJob();
        CandidateCv cv = new CandidateCv();
        cv.setId(UUID.randomUUID());
        cv.setTitle("CV");
        cv.setOriginalFileName("cv.pdf");
        cv.setStorageKey("opaque-key");
        cv.setContentType("application/pdf");
        ApplicationResponse mapped = org.mockito.Mockito.mock(ApplicationResponse.class);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(applicationRepository.existsByCandidateIdAndJobId(candidate.getId(), job.getId())).thenReturn(false);
        when(candidateCvRepository.findByIdAndCandidateIdAndSourceTypeAndDeletedAtIsNull(cv.getId(), candidate.getId(), "uploaded"))
                .thenReturn(Optional.of(cv));
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> {
            Application saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(notificationRepository.save(any())).thenAnswer(invocation -> {
            var note = invocation.getArgument(0);
            return note;
        });
        when(historyRepository.findByApplicationIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(interviewScheduleRepository.findByApplicationId(any())).thenReturn(List.of());
        when(jobOfferRepository.findByApplicationId(any())).thenReturn(Optional.empty());
        when(jobService.toJobResponse(job, candidate)).thenReturn(null);
        when(dtoMapper.toApplicationResponse(any(), any(), any(), any(), any())).thenReturn(mapped);

        ApplicationResponse response = applicationService.submit(request(job.getId(), cv.getId()));

        assertSame(mapped, response);
        verify(featureLimitService).consumeApplication(candidate.getUser());
        verify(applicationRepository).save(any(Application.class));
    }

    @Test
    void seedStatus_updatesStatusAndWritesHistory() {
        Application application = new Application();
        application.setId(UUID.randomUUID());
        application.setStatus(Application.ApplicationStatus.SUBMITTED);
        CandidateProfile candidate = candidate();
        application.setCandidate(candidate);
        when(applicationRepository.save(application)).thenReturn(application);
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        applicationService.seedStatus(application, Application.ApplicationStatus.SHORTLISTED, "Mời phỏng vấn");

        assertEquals(Application.ApplicationStatus.SHORTLISTED, application.getStatusEnum());
        verify(historyRepository).save(any());
    }

    @Test
    void updateStatus_notFound() {
        UUID id = UUID.randomUUID();
        when(applicationRepository.findById(id)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> applicationService.updateStatus(id, Application.ApplicationStatus.REJECTED, "Không phù hợp"));
        assertEquals("APPLICATION_NOT_FOUND", ex.getCode());
    }

    private CandidateProfile readyCandidate() {
        CandidateProfile candidate = candidate();
        when(candidateService.getCurrentCandidateProfile()).thenReturn(candidate);
        doNothing().when(candidateService).requireCandidate(candidate.getUser());
        doNothing().when(featureLimitService).requireApplication(candidate.getUser());
        when(candidateService.isApplyReady(candidate)).thenReturn(true);
        return candidate;
    }

    private static CandidateProfile candidate() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Nguyen Van A");
        user.setEmail("candidate@srp.test");
        CandidateProfile candidate = new CandidateProfile();
        candidate.setId(UUID.randomUUID());
        candidate.setUser(user);
        return candidate;
    }

    private static Job publishedJob() {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setTitle("Java Developer");
        job.setStatus("published");
        job.setDeadline(LocalDate.now().plusDays(7));
        return job;
    }

    private static ApplicationSubmitRequest request(UUID jobId, UUID cvId) {
        return new ApplicationSubmitRequest(jobId.toString(), cvId.toString(), null, "Hanoi", "Cover");
    }
}
