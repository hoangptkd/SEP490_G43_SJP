package com.sjp.recruitment.service;

import com.cloudinary.Cloudinary;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.EmployerDashboardResponse;
import com.sjp.recruitment.model.entity.Employer;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.ApplicationRepository;
import com.sjp.recruitment.repository.CategoryRepository;
import com.sjp.recruitment.repository.CompanyDocumentRepository;
import com.sjp.recruitment.repository.CompanyIndustryRepository;
import com.sjp.recruitment.repository.CompanyLocationRepository;
import com.sjp.recruitment.repository.CompanyRepository;
import com.sjp.recruitment.repository.EmployerRepository;
import com.sjp.recruitment.repository.InterviewScheduleRepository;
import com.sjp.recruitment.repository.JobOfferRepository;
import com.sjp.recruitment.repository.JobRepository;
import com.sjp.recruitment.repository.NotificationRepository;
import com.sjp.recruitment.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmployerServiceDashboardTest {

    @Mock private AuthService authService;
    @Mock private EmployerRepository employerRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private CompanyLocationRepository companyLocationRepository;
    @Mock private CompanyIndustryRepository companyIndustryRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private CompanyDocumentRepository companyDocumentRepository;
    @Mock private JobRepository jobRepository;
    @Mock private JobService jobService;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationService applicationService;
    @Mock private InterviewScheduleRepository interviewScheduleRepository;
    @Mock private CandidateService candidateService;
    @Mock private Cloudinary cloudinary;
    @Mock private DtoMapper dtoMapper;
    @Mock private FeatureLimitService featureLimitService;
    @Mock private SystemSettingsService systemSettingsService;
    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private JobOfferRepository jobOfferRepository;
    @Mock private TaxCodeLookupService taxCodeLookupService;

    @InjectMocks private EmployerService employerService;

    @Test
    void getDashboardStats_rejectsNonEmployer() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(User.UserRole.CANDIDATE);
        when(authService.getCurrentUser()).thenReturn(user);

        ApiException ex = assertThrows(ApiException.class, () -> employerService.getDashboardStats(null, null));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void getDashboardStats_returnsZeroedCountsWhenEmployerHasNoJobs() {
        Employer employer = stubEmployer();
        stubEmptyDashboard(employer.getId(), employer.getUser().getId());

        EmployerDashboardResponse response = employerService.getDashboardStats(null, null);

        assertEquals(0, response.totalJobs());
        assertEquals(0, response.activeJobs());
        assertEquals(0, response.totalApplications());
        assertEquals(14, response.applicationTrend().size());
        assertTrue(response.pendingTasks().isEmpty());
        assertTrue(response.activeJobsList().isEmpty());
    }

    @Test
    void getDashboardStats_withDateFilterBuildsTrendForEachDay() {
        Employer employer = stubEmployer();
        stubEmptyDashboard(employer.getId(), employer.getUser().getId());
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 3);

        EmployerDashboardResponse response = employerService.getDashboardStats(start, end);

        assertEquals(3, response.applicationTrend().size());
        assertEquals("2026-08-01", response.applicationTrend().get(0).date());
        assertEquals("2026-08-03", response.applicationTrend().get(2).date());
        assertEquals(0, response.applicationTrend().get(0).count());
    }

    private Employer stubEmployer() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(User.UserRole.EMPLOYER);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailVerified(true);

        Employer employer = new Employer();
        employer.setId(UUID.randomUUID());
        employer.setUser(user);
        when(authService.getCurrentUser()).thenReturn(user);
        when(employerRepository.findByUserId(user.getId())).thenReturn(Optional.of(employer));
        return employer;
    }

    private void stubEmptyDashboard(UUID employerId, UUID userId) {
        when(jobRepository.countByEmployerIdAndStatusNot(employerId, "archived")).thenReturn(0L);
        when(jobRepository.findByEmployerIdAndStatus(employerId, "published")).thenReturn(List.of());
        when(jobRepository.findByEmployerIdAndStatusNot(employerId, "archived")).thenReturn(List.of());
        when(applicationRepository.countApplicationsByStatusForEmployerAndJobStatus(employerId, "published"))
                .thenReturn(List.of());
        when(applicationRepository.countApplicationsByStatusForEmployerAndJobStatusAndDateRange(
                eq(employerId), eq("published"), any(), any())).thenReturn(List.of());
        when(applicationRepository.findByEmployerIdAndJobStatus(eq(employerId), eq("published"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(applicationRepository.findByEmployerIdAndJobStatusAndDateRange(
                eq(employerId), eq("published"), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(applicationService.toResponseBulk(any())).thenReturn(List.of());
        when(applicationRepository.findApplicationDatesByEmployerSinceAndJobStatus(eq(employerId), any(), eq("published")))
                .thenReturn(List.of());
        when(applicationRepository.findApplicationDatesByEmployerAndJobStatusAndDateRange(
                eq(employerId), eq("published"), any(), any())).thenReturn(List.of());
        when(applicationRepository.findByJobEmployerIdAndStatusAndJobStatusOrderBySubmittedAtDesc(
                eq(employerId), anyString(), eq("published"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(interviewScheduleRepository.findByEmployerIdAndStatusAndJobStatusOrderByScheduledAtDesc(
                eq(employerId), anyString(), eq("published"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(interviewScheduleRepository.findCompletedInterviewsWithoutOfferAndJobStatus(
                eq(employerId), eq("published"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(jobOfferRepository.findByApplicationJobEmployerIdAndStatusAndJobStatusOrderByCreatedAtDesc(
                eq(employerId), anyString(), eq("published"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(interviewScheduleRepository.findByEmployerIdAndScheduledAtAfterAndJobStatusOrderByScheduledAtAsc(
                eq(employerId), any(), eq("published"))).thenReturn(List.of());
        when(interviewScheduleRepository.findByEmployerIdAndScheduledAtBetweenAndJobStatusOrderByScheduledAtAsc(
                eq(employerId), any(), any(), eq("published"))).thenReturn(List.of());
        when(notificationRepository.countByRecipientUserIdAndReadFalse(userId)).thenReturn(0L);
        when(interviewScheduleRepository.countActiveInterviewsByStatusesAndJobStatus(eq(employerId), any(), eq("published")))
                .thenReturn(0L);
        when(interviewScheduleRepository.countActiveInterviewsByStatusesAndJobStatusAndDateRange(
                eq(employerId), any(), eq("published"), any(), any())).thenReturn(0L);
        when(interviewScheduleRepository.countByEmployerIdAndStatusAndJobStatus(eq(employerId), anyString(), eq("published")))
                .thenReturn(0L);
        when(interviewScheduleRepository.countByEmployerIdAndStatusAndScheduledAtBetween(
                eq(employerId), anyString(), any(), any())).thenReturn(0L);
        when(interviewScheduleRepository.countInterviewsByEmployerAndJobStatusAndDateRange(
                eq(employerId), eq("published"), any(), any())).thenReturn(0L);
        when(jobOfferRepository.countActiveOffersByStatusesAndJobStatus(eq(employerId), any(), eq("published")))
                .thenReturn(0L);
        when(jobOfferRepository.countActiveOffersByStatusesAndJobStatusAndDateRange(
                eq(employerId), any(), eq("published"), any(), any())).thenReturn(0L);
        when(notificationRepository.findByRecipientUserId(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
    }
}
