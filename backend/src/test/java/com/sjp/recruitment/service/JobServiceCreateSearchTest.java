package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.entity.Company;
import com.sjp.recruitment.model.entity.Employer;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.repository.ApplicationRepository;
import com.sjp.recruitment.repository.ApplicationStatusHistoryRepository;
import com.sjp.recruitment.repository.CandidateProfileRepository;
import com.sjp.recruitment.repository.CompanyLocationRepository;
import com.sjp.recruitment.repository.CompanyRepository;
import com.sjp.recruitment.repository.InterviewScheduleRepository;
import com.sjp.recruitment.repository.JobEditHistoryRepository;
import com.sjp.recruitment.repository.JobRepository;
import com.sjp.recruitment.repository.JobSkillRepository;
import com.sjp.recruitment.repository.NotificationRepository;
import com.sjp.recruitment.repository.SavedJobRepository;
import com.sjp.recruitment.repository.SkillRepository;
import com.sjp.recruitment.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceCreateSearchTest {

    @Mock private JobRepository jobRepository;
    @Mock private UserRepository userRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private CompanyLocationRepository companyLocationRepository;
    @Mock private CandidateProfileRepository candidateProfileRepository;
    @Mock private SavedJobRepository savedJobRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private JobSkillRepository jobSkillRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private ApplicationStatusHistoryRepository applicationStatusHistoryRepository;
    @Mock private JobEditHistoryRepository jobEditHistoryRepository;
    @Mock private InterviewScheduleRepository interviewScheduleRepository;
    @Mock private DtoMapper dtoMapper;
    @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Mock private SystemSettingsService systemSettingsService;
    @Mock private FeatureLimitService featureLimitService;
    @Mock private AuthService authService;
    @Mock private CandidateRealtimeEventPublisher realtimeEventPublisher;

    @InjectMocks private JobService jobService;

    @Test
    void search_rejectsInvertedSalaryRange() {
        ApiException ex = assertThrows(ApiException.class, () -> jobService.search(
                null, null, new BigDecimal("2000"), new BigDecimal("1000"),
                null, null, null, null, null, "newest", 0, 10
        ));
        assertEquals("SALARY_RANGE_INVALID", ex.getCode());
    }

    @Test
    void findById_rejectsInvalidUuid() {
        ApiException ex = assertThrows(ApiException.class, () -> jobService.findById("abc"));
        assertEquals("JOB_ID_INVALID", ex.getCode());
    }

    @Test
    void findById_notFound() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> jobService.findById(id.toString()));
        assertEquals("JOB_NOT_FOUND", ex.getCode());
    }

    @Test
    void findById_returnsJob() {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        assertEquals(job, jobService.findById(job.getId().toString()));
    }

    @Test
    void create_requiresEmployer() {
        ApiException ex = assertThrows(ApiException.class, () -> jobService.create(null, null));
        assertEquals("EMPLOYER_REQUIRED", ex.getCode());
    }

    @Test
    void create_requiresCompany() {
        Employer employer = new Employer();
        ApiException ex = assertThrows(ApiException.class, () -> jobService.create(null, employer));
        assertEquals("COMPANY_REQUIRED", ex.getCode());
    }

    @Test
    void create_rejectsUnverifiedCompanyWhenReviewRequired() {
        Employer employer = new Employer();
        Company company = new Company();
        company.setVerificationStatus("pending");
        employer.setCompany(company);
        when(systemSettingsService.isCompanyReviewRequired()).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> jobService.create(null, employer));
        assertEquals("COMPANY_NOT_VERIFIED", ex.getCode());
    }
}
