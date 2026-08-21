package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.CompanyLocationRequest;
import com.sjp.recruitment.model.dto.request.CompanyProfileRequest;
import com.sjp.recruitment.model.dto.request.EmployerPersonalProfileRequest;
import com.sjp.recruitment.model.dto.request.JobRequest;
import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployerControllerTest {

    @Mock private EmployerService employerService;
    @Mock private AiRankingService aiRankingService;
    @Mock private AuthService authService;
    @Mock private FeatureLimitService featureLimitService;
    @Mock private TaxCodeLookupService taxCodeLookupService;
    @InjectMocks private EmployerController controller;

    @Test
    void getCompanyProfile_delegatesToService() {
        CompanyProfileResponse expected = mock(CompanyProfileResponse.class);
        when(employerService.getCompanyProfile()).thenReturn(expected);
        assertSame(expected, controller.getCompanyProfile().getBody());
    }

    @Test
    void updateCompanyProfile_delegatesToService() {
        CompanyProfileRequest request = mock(CompanyProfileRequest.class);
        CompanyProfileResponse expected = mock(CompanyProfileResponse.class);
        when(employerService.updateCompanyProfile(request)).thenReturn(expected);
        assertSame(expected, controller.updateCompanyProfile(request).getBody());
    }

    @Test
    void updatePersonalProfile_delegatesAndReturnsMessage() {
        EmployerPersonalProfileRequest request = mock(EmployerPersonalProfileRequest.class);
        ResponseEntity<MessageResponse> response = controller.updatePersonalProfile(request);
        verify(employerService).updatePersonalProfile(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void lookupTaxCode_delegatesToService() {
        TaxCodeLookupResponse expected = mock(TaxCodeLookupResponse.class);
        when(taxCodeLookupService.lookup("0123456789")).thenReturn(expected);
        assertSame(expected, controller.lookupTaxCode("0123456789").getBody());
    }

    @Test
    void getCompanyLocations_delegatesToService() {
        List<CompanyLocationResponse> expected = List.of(mock(CompanyLocationResponse.class));
        when(employerService.getCompanyLocations()).thenReturn(expected);
        assertSame(expected, controller.getCompanyLocations().getBody());
    }

    @Test
    void createCompanyLocation_delegatesToService() {
        CompanyLocationRequest request = mock(CompanyLocationRequest.class);
        CompanyLocationResponse expected = mock(CompanyLocationResponse.class);
        when(employerService.createCompanyLocation(request)).thenReturn(expected);
        assertSame(expected, controller.createCompanyLocation(request).getBody());
    }

    @Test
    void deleteCompanyLocation_delegatesAndReturnsNoContent() {
        ResponseEntity<Void> response = controller.deleteCompanyLocation("loc-1");
        verify(employerService).deleteCompanyLocation("loc-1");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void createJob_delegatesToService() {
        JobRequest request = mock(JobRequest.class);
        JobResponse expected = mock(JobResponse.class);
        when(employerService.createJob(request)).thenReturn(expected);
        assertSame(expected, controller.createJob(request).getBody());
    }

    @Test
    void updateJob_delegatesToService() {
        JobRequest request = mock(JobRequest.class);
        JobResponse expected = mock(JobResponse.class);
        when(employerService.updateJob("job-1", request)).thenReturn(expected);
        assertSame(expected, controller.updateJob("job-1", request).getBody());
    }

    @Test
    void deleteJob_delegatesAndReturnsNoContent() {
        ResponseEntity<Void> response = controller.deleteJob("job-1");
        verify(employerService).deleteJob("job-1");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void closeJob_delegatesToService() {
        JobResponse expected = mock(JobResponse.class);
        when(employerService.closeJob("job-1")).thenReturn(expected);
        assertSame(expected, controller.closeJob("job-1").getBody());
    }

    @Test
    void reopenJob_delegatesToService() {
        JobResponse expected = mock(JobResponse.class);
        Map<String, String> body = Map.of("deadline", "2025-12-31");
        when(employerService.reopenJob("job-1", "2025-12-31")).thenReturn(expected);
        assertSame(expected, controller.reopenJob("job-1", null, body).getBody());
    }

    @Test
    void markNotificationRead_returnsNoContent() {
        ResponseEntity<Void> response = controller.markNotificationRead("n-1");
        verify(employerService).markNotificationRead("n-1");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void markAllNotificationsRead_returnsNoContent() {
        ResponseEntity<Void> response = controller.markAllNotificationsRead();
        verify(employerService).markAllNotificationsRead();
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void getAiRankingQuota_delegatesToService() {
        User user = mock(User.class);
        Map<String, Object> expected = Map.of("used", 5, "limit", 100);
        when(authService.getCurrentUser()).thenReturn(user);
        when(aiRankingService.getAiRankingQuota(user)).thenReturn(expected);
        assertSame(expected, controller.getAiRankingQuota().getBody());
    }
}
