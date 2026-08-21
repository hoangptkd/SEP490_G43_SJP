package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.ApplicationSubmitRequest;
import com.sjp.recruitment.model.dto.response.ApplicationResponse;
import com.sjp.recruitment.model.dto.response.PageResponse;
import com.sjp.recruitment.service.ApplicationService;
import com.sjp.recruitment.service.CandidateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationControllerTest {

    @Mock private ApplicationService applicationService;
    @InjectMocks private ApplicationController applicationController;

    @Test
    void classLevelPreAuthorizeRequiresCandidateRole() {
        PreAuthorize annotation = ApplicationController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation);
        assertEquals("hasRole('CANDIDATE')", annotation.value());
    }

    @Test
    void submit_delegatesToService() {
        ApplicationSubmitRequest request = mock(ApplicationSubmitRequest.class);
        ApplicationResponse expected = mock(ApplicationResponse.class);
        when(applicationService.submit(request)).thenReturn(expected);

        ResponseEntity<ApplicationResponse> response = applicationController.submit(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test
    @SuppressWarnings("unchecked")
    void myApplications_delegatesWithPagination() {
        PageResponse<ApplicationResponse> expected = mock(PageResponse.class);
        when(applicationService.myApplications(0, 10)).thenReturn(expected);

        ResponseEntity<PageResponse<ApplicationResponse>> response = applicationController.myApplications(0, 10);

        assertSame(expected, response.getBody());
    }

    @Test
    void myApplication_delegatesById() {
        ApplicationResponse expected = mock(ApplicationResponse.class);
        when(applicationService.myApplication("app-1")).thenReturn(expected);

        ResponseEntity<ApplicationResponse> response = applicationController.myApplication("app-1");

        assertSame(expected, response.getBody());
    }

    @Test
    void downloadSubmittedResume_returnsFileWithHeaders() {
        CandidateService.CvDownload download = new CandidateService.CvDownload(
                "resume.pdf", "application/pdf", new ByteArrayResource(new byte[]{1, 2, 3}));
        when(applicationService.downloadMySubmittedCv("app-1")).thenReturn(download);

        ResponseEntity<?> response = applicationController.downloadSubmittedResume("app-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().getContentType().toString().contains("application/pdf"));
    }
}
