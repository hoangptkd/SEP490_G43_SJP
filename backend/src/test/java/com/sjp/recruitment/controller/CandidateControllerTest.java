package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.CandidateProfileRequest;
import com.sjp.recruitment.model.dto.request.CandidateOnboardingRequest;
import com.sjp.recruitment.model.dto.request.CvVersionRequest;
import com.sjp.recruitment.model.dto.request.JobAlertRequest;
import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.service.CandidateService;
import com.sjp.recruitment.service.JobAlertService;
import com.sjp.recruitment.service.JobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateControllerTest {

    @Mock private CandidateService candidateService;
    @Mock private JobService jobService;
    @Mock private JobAlertService jobAlertService;
    @InjectMocks private CandidateController controller;

    @Test
    void getProfile_delegatesToService() {
        CandidateProfileResponse expected = mock(CandidateProfileResponse.class);
        when(candidateService.getProfile()).thenReturn(expected);
        assertSame(expected, controller.getProfile().getBody());
    }

    @Test
    void updateProfile_delegatesToService() {
        CandidateProfileRequest request = mock(CandidateProfileRequest.class);
        CandidateProfileResponse expected = mock(CandidateProfileResponse.class);
        when(candidateService.updateProfile(request)).thenReturn(expected);
        assertSame(expected, controller.updateProfile(request).getBody());
    }

    @Test
    void getOnboarding_delegatesToService() {
        CandidateOnboardingResponse expected = mock(CandidateOnboardingResponse.class);
        when(candidateService.getOnboarding()).thenReturn(expected);
        assertSame(expected, controller.getOnboarding().getBody());
    }

    @Test
    void completeOnboarding_delegatesToService() {
        CandidateOnboardingRequest request = mock(CandidateOnboardingRequest.class);
        CandidateOnboardingResponse expected = mock(CandidateOnboardingResponse.class);
        when(candidateService.completeOnboarding(request)).thenReturn(expected);
        assertSame(expected, controller.completeOnboarding(request).getBody());
    }

    @Test
    void skipOnboarding_delegatesToService() {
        CandidateOnboardingResponse expected = mock(CandidateOnboardingResponse.class);
        when(candidateService.skipOnboarding()).thenReturn(expected);
        assertSame(expected, controller.skipOnboarding().getBody());
    }

    @Test
    void jobTitleSuggestions_delegatesToService() {
        when(candidateService.jobTitleSuggestions("dev", 5)).thenReturn(List.of("Developer"));
        assertEquals(List.of("Developer"), controller.jobTitleSuggestions("dev", 5).getBody());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getCvs_delegatesWithPagination() {
        PageResponse<CvResponse> expected = mock(PageResponse.class);
        when(candidateService.getCvs(0, 20)).thenReturn(expected);
        assertSame(expected, controller.getCvs(0, 20).getBody());
    }

    @Test
    void setDefaultCv_delegatesToService() {
        CvResponse expected = mock(CvResponse.class);
        when(candidateService.setDefaultCv("cv-1")).thenReturn(expected);
        assertSame(expected, controller.setDefaultCv("cv-1").getBody());
    }

    @Test
    void deleteCv_delegatesAndReturnsNoContent() {
        ResponseEntity<Void> response = controller.deleteCv("cv-1");
        verify(candidateService).deleteCv("cv-1");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void saveJob_delegatesAndReturnsNoContent() {
        ResponseEntity<Void> response = controller.saveJob("job-1");
        verify(candidateService).saveJob("job-1");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void unsaveJob_delegatesAndReturnsNoContent() {
        ResponseEntity<Void> response = controller.unsaveJob("job-1");
        verify(candidateService).unsaveJob("job-1");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recommendations_delegatesToJobService() {
        List<RecommendationResponse> expected = List.of(mock(RecommendationResponse.class));
        when(jobService.recommendations()).thenReturn(expected);
        assertSame(expected, controller.recommendations().getBody());
    }

    @Test
    void markNotificationRead_returnsNoContent() {
        ResponseEntity<Void> response = controller.markNotificationRead("n-1");
        verify(candidateService).markNotificationRead("n-1");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void markAllNotificationsRead_returnsNoContent() {
        ResponseEntity<Void> response = controller.markAllNotificationsRead();
        verify(candidateService).markAllNotificationsRead();
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void getSubscription_delegatesToService() {
        SubscriptionResponse expected = mock(SubscriptionResponse.class);
        when(candidateService.getSubscription()).thenReturn(expected);
        assertSame(expected, controller.subscription().getBody());
    }

    @Test
    @SuppressWarnings("unchecked")
    void jobAlerts_delegatesToService() {
        PageResponse<JobAlertResponse> expected = mock(PageResponse.class);
        when(jobAlertService.list(0, 10)).thenReturn(expected);
        assertSame(expected, controller.jobAlerts(0, 10).getBody());
    }

    @Test
    void createJobAlert_delegatesToService() {
        JobAlertRequest request = mock(JobAlertRequest.class);
        JobAlertResponse expected = mock(JobAlertResponse.class);
        when(jobAlertService.create(request)).thenReturn(expected);
        assertSame(expected, controller.createJobAlert(request).getBody());
    }

    @Test
    void deleteJobAlert_delegatesAndReturnsNoContent() {
        ResponseEntity<Void> response = controller.deleteJobAlert("alert-1");
        verify(jobAlertService).delete("alert-1");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }
}
