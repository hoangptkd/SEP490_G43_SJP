package com.sjp.recruitment.service;

import com.sjp.recruitment.model.dto.request.InterviewCandidateResponseRequest;
import com.sjp.recruitment.model.dto.request.InterviewScheduleRequest;
import com.sjp.recruitment.model.dto.response.InterviewScheduleResponse;
import com.sjp.recruitment.model.entity.*;
import com.sjp.recruitment.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationWorkflowInterviewTest {
    @Mock private ApplicationRepository applicationRepository;
    @Mock private InterviewScheduleRepository interviewScheduleRepository;
    @Mock private JobOfferRepository jobOfferRepository;
    @Mock private EmailService emailService;
    @Mock private DtoMapper dtoMapper;
    @Mock private ApplicationService applicationService;
    @Mock private EmployerRepository employerRepository;
    @Mock private NotificationRepository notificationRepository;

    @InjectMocks private ApplicationWorkflowService workflowService;

    @Test
    void newlyScheduledInterviewWaitsForCandidateResponseWithoutDeadline() {
        Application application = application();
        UUID employerId = application.getJob().getEmployer().getId();
        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(2);
        InterviewScheduleResponse expected = mock(InterviewScheduleResponse.class);
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(interviewScheduleRepository.findByApplicationId(application.getId())).thenReturn(List.of());
        when(interviewScheduleRepository.save(any(InterviewSchedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toInterviewScheduleResponse(any())).thenReturn(expected);

        InterviewScheduleResponse actual = workflowService.scheduleInterview(
                application.getId(), employerId,
                new InterviewScheduleRequest(scheduledAt, "https://meet.example", null, "Vòng kỹ thuật")
        );

        ArgumentCaptor<InterviewSchedule> captor = ArgumentCaptor.forClass(InterviewSchedule.class);
        verify(interviewScheduleRepository).save(captor.capture());
        assertEquals("PENDING_RESPONSE", captor.getValue().getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(captor.getValue().getResponseDeadline());
        assertEquals(expected, actual);
        verify(applicationService).seedStatus(
                application, Application.ApplicationStatus.INTERVIEW_SCHEDULED, "Đã lên lịch phỏng vấn");
    }

    @Test
    void candidateCanRequestRescheduleWithoutConfirmingAttendanceFirst() {
        InterviewSchedule schedule = schedule("SCHEDULED");
        when(interviewScheduleRepository.findByIdAndCandidateId(schedule.getId(), schedule.getCandidate().getId()))
                .thenReturn(Optional.of(schedule));
        when(interviewScheduleRepository.save(schedule)).thenReturn(schedule);
        when(employerRepository.findByCompanyId(any())).thenReturn(List.of());

        workflowService.candidateRespondToInterview(
                schedule.getId(), schedule.getCandidate().getId(),
                new InterviewCandidateResponseRequest("request_reschedule", "Xin chuyển sang buổi chiều")
        );

        assertEquals("RESCHEDULE_REQUESTED", schedule.getStatus());
        verify(applicationService).seedStatus(
                eq(schedule.getApplication()),
                eq(schedule.getApplication().getStatusEnum()),
                eq("Ứng viên đã yêu cầu đổi lịch phỏng vấn"));
    }

    @Test
    void candidateCanDeclineScheduledInterview() {
        InterviewSchedule schedule = schedule("SCHEDULED");
        when(interviewScheduleRepository.findByIdAndCandidateId(schedule.getId(), schedule.getCandidate().getId()))
                .thenReturn(Optional.of(schedule));
        when(interviewScheduleRepository.save(schedule)).thenReturn(schedule);
        when(employerRepository.findByCompanyId(any())).thenReturn(List.of());

        workflowService.candidateRespondToInterview(
                schedule.getId(), schedule.getCandidate().getId(),
                new InterviewCandidateResponseRequest("declined", null)
        );

        assertEquals("DECLINED", schedule.getStatus());
    }

    private InterviewSchedule schedule(String status) {
        Application application = application();
        InterviewSchedule schedule = new InterviewSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setApplication(application);
        schedule.setCandidate(application.getCandidate());
        schedule.setEmployer(application.getJob().getEmployer());
        schedule.setScheduledAt(LocalDateTime.now().plusDays(2));
        schedule.setStatus(status);
        return schedule;
    }

    private Application application() {
        User candidateUser = new User();
        candidateUser.setId(UUID.randomUUID());
        candidateUser.setEmail("candidate@example.com");
        CandidateProfile candidate = new CandidateProfile();
        candidate.setId(UUID.randomUUID());
        candidate.setUser(candidateUser);
        candidate.setFullName("Nguyễn Văn A");

        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("SJP Technology");
        Employer employer = new Employer();
        employer.setId(UUID.randomUUID());
        employer.setCompany(company);
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setTitle("Java Developer");
        job.setCompany(company);
        job.setEmployer(employer);

        Application application = new Application();
        application.setId(UUID.randomUUID());
        application.setCandidate(candidate);
        application.setJob(job);
        application.setStatus(Application.ApplicationStatus.SHORTLISTED);
        return application;
    }
}
