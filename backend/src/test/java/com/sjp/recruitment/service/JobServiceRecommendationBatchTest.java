package com.sjp.recruitment.service;

import com.sjp.recruitment.model.dto.response.JobResponse;
import com.sjp.recruitment.model.entity.*;
import com.sjp.recruitment.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceRecommendationBatchTest {
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
    @Mock private org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Mock private SystemSettingsService systemSettingsService;
    @Mock private FeatureLimitService featureLimitService;
    @Mock private AuthService authService;
    @InjectMocks private JobService jobService;

    private User candidateUser;
    private CandidateProfile candidate;

    @BeforeEach
    void setUp() {
        candidateUser = new User();
        candidateUser.setId(UUID.randomUUID());
        candidateUser.setRole(User.UserRole.CANDIDATE);
        candidateUser.setStatus(User.UserStatus.ACTIVE);
        candidateUser.setEmailVerified(true);

        candidate = new CandidateProfile();
        candidate.setId(UUID.randomUUID());
        candidate.setUser(candidateUser);
        candidate.setSkills(List.of("Java"));

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(candidateUser);
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recommendationsBatchLoadsPerJobState() {
        Job job = recommendationJob();
        UUID jobId = job.getId();
        UUID employerUserId = job.getEmployer().getUser().getId();
        JobResponse jobResponse = mock(JobResponse.class);

        when(candidateProfileRepository.findWithSkillsByUserId(candidateUser.getId()))
                .thenReturn(Optional.of(candidate));
        when(jobRepository.findRecommendationJobIds(any(Pageable.class)))
                .thenReturn(List.of(jobId));
        when(jobRepository.findRecommendationJobsByIds(List.of(jobId))).thenReturn(List.of(job));
        when(savedJobRepository.findSavedJobIds(candidate.getId(), List.of(jobId))).thenReturn(List.of(jobId));
        when(applicationRepository.findAppliedJobIds(candidate.getId(), List.of(jobId))).thenReturn(List.of(jobId));
        when(applicationRepository.countByJobIds(List.of(jobId)))
                .thenReturn(List.<Object[]>of(new Object[]{jobId, 4L}));
        when(featureLimitService.resolveListingPrioritiesForUsers(anyCollection()))
                .thenReturn(Map.of(employerUserId, 2));
        when(dtoMapper.toJobResponse(eq(job), eq(true), eq(true), anyInt(), eq(4L), eq(2)))
                .thenReturn(jobResponse);

        var recommendations = jobService.recommendations();

        assertEquals(1, recommendations.size());
        assertEquals(List.of("Java"), recommendations.get(0).matchedSkills());
        verify(jobRepository).findRecommendationJobIds(argThat(page -> page.getPageSize() == 20));
        verify(savedJobRepository, never()).existsByCandidateIdAndJobId(any(), any());
        verify(applicationRepository, never()).existsByCandidateIdAndJobId(any(), any());
        verify(applicationRepository, never()).countByJobId(any());
        verify(featureLimitService, never()).resolveListingPriorityForUser(any());
    }

    private Job recommendationJob() {
        User employerUser = new User();
        employerUser.setId(UUID.randomUUID());
        Employer employer = new Employer();
        employer.setUser(employerUser);

        Skill skill = new Skill();
        skill.setName("Java");
        JobSkill jobSkill = new JobSkill();
        jobSkill.setSkill(skill);

        Company company = new Company();
        company.setName("SJP Technology");

        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setTitle("Java Backend Developer");
        job.setStatus("published");
        job.setCreatedAt(LocalDateTime.now());
        job.setEmployer(employer);
        job.setCompany(company);
        job.setJobSkills(List.of(jobSkill));
        return job;
    }
}
