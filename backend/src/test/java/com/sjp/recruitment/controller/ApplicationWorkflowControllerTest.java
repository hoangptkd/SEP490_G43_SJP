package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.*;
import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.Employer;
import com.sjp.recruitment.service.ApplicationWorkflowService;
import com.sjp.recruitment.service.CandidateService;
import com.sjp.recruitment.service.EmployerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationWorkflowControllerTest {

    @Mock private ApplicationWorkflowService workflowService;
    @Mock private EmployerService employerService;
    @Mock private CandidateService candidateService;
    @InjectMocks private ApplicationWorkflowController controller;

    @Test
    void rejectApplication_delegatesWithEmployerId() {
        UUID appId = UUID.randomUUID();
        UUID employerId = UUID.randomUUID();
        Employer employer = mock(Employer.class);
        when(employer.getId()).thenReturn(employerId);
        when(employerService.getCurrentEmployerOrRegisterPlaceholder()).thenReturn(employer);

        controller.rejectApplication(appId, "not suitable");

        verify(workflowService).rejectApplication(appId, employerId, "not suitable");
    }

    @Test
    void scheduleInterview_delegatesAndReturnsResponse() {
        UUID appId = UUID.randomUUID();
        UUID employerId = UUID.randomUUID();
        Employer employer = mock(Employer.class);
        when(employer.getId()).thenReturn(employerId);
        when(employerService.getCurrentEmployerOrRegisterPlaceholder()).thenReturn(employer);
        InterviewScheduleRequest request = mock(InterviewScheduleRequest.class);
        InterviewScheduleResponse expected = mock(InterviewScheduleResponse.class);
        when(workflowService.scheduleInterview(appId, employerId, request)).thenReturn(expected);

        assertSame(expected, controller.scheduleInterview(appId, request).getBody());
    }

    @Test
    void candidateRespondToInterview_delegatesToWorkflowService() {
        UUID interviewId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        CandidateProfile candidate = mock(CandidateProfile.class);
        when(candidate.getId()).thenReturn(candidateId);
        when(candidateService.getCurrentCandidateProfile()).thenReturn(candidate);
        InterviewCandidateResponseRequest request = mock(InterviewCandidateResponseRequest.class);
        InterviewScheduleResponse expected = mock(InterviewScheduleResponse.class);
        when(workflowService.candidateRespondToInterview(interviewId, candidateId, request)).thenReturn(expected);

        assertSame(expected, controller.candidateRespondToInterview(interviewId, request).getBody());
    }

    @Test
    void createJobOffer_delegatesWithEmployerId() {
        UUID appId = UUID.randomUUID();
        UUID employerId = UUID.randomUUID();
        Employer employer = mock(Employer.class);
        when(employer.getId()).thenReturn(employerId);
        when(employerService.getCurrentEmployerOrRegisterPlaceholder()).thenReturn(employer);
        JobOfferRequest request = mock(JobOfferRequest.class);
        JobOfferResponse expected = mock(JobOfferResponse.class);
        when(workflowService.createJobOffer(appId, employerId, request)).thenReturn(expected);

        assertSame(expected, controller.createJobOffer(appId, request).getBody());
    }

    @Test
    void candidateRespondToOffer_delegatesWithCandidateId() {
        UUID offerId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        CandidateProfile candidate = mock(CandidateProfile.class);
        when(candidate.getId()).thenReturn(candidateId);
        when(candidateService.getCurrentCandidateProfile()).thenReturn(candidate);
        CandidateOfferResponseRequest request = mock(CandidateOfferResponseRequest.class);
        JobOfferResponse expected = mock(JobOfferResponse.class);
        when(workflowService.candidateRespondToOffer(offerId, candidateId, request)).thenReturn(expected);

        assertSame(expected, controller.candidateRespondToOffer(offerId, request).getBody());
    }
}
