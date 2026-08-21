package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.response.JobPageResponse;
import com.sjp.recruitment.model.dto.response.JobReportResponse;
import com.sjp.recruitment.model.dto.response.JobResponse;
import com.sjp.recruitment.model.dto.response.RecommendationResponse;
import com.sjp.recruitment.model.dto.request.JobReportRequest;
import com.sjp.recruitment.service.JobReportService;
import com.sjp.recruitment.service.JobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock private JobService jobService;
    @Mock private JobReportService jobReportService;
    @InjectMocks private JobController controller;

    @Test
    void getAllJobs_delegatesWithAllParams() {
        JobPageResponse expected = mock(JobPageResponse.class);
        when(jobService.search(null, null, null, null, null, null, null, null, null, "newest", 0, 10))
                .thenReturn(expected);
        assertSame(expected, controller.getAllJobs(null, null, null, null, null, null, null, null, null, "newest", 0, 10).getBody());
    }

    @Test
    void getJobById_delegatesToService() {
        JobResponse expected = mock(JobResponse.class);
        when(jobService.findJobResponseById("j-1")).thenReturn(expected);
        assertSame(expected, controller.getJobById("j-1").getBody());
    }

    @Test
    void reportJob_requiresCandidateRole() throws Exception {
        var method = JobController.class.getMethod("reportJob", String.class, JobReportRequest.class);
        var policy = method.getAnnotation(PreAuthorize.class);
        assertEquals("hasRole('CANDIDATE')", policy.value());
    }

    @Test
    void reportJob_delegatesToService() {
        JobReportRequest request = mock(JobReportRequest.class);
        JobReportResponse expected = mock(JobReportResponse.class);
        when(jobReportService.reportJob("j-1", request)).thenReturn(expected);
        assertSame(expected, controller.reportJob("j-1", request).getBody());
    }

    @Test
    void recommendations_delegatesToService() {
        List<RecommendationResponse> expected = List.of(mock(RecommendationResponse.class));
        when(jobService.recommendations()).thenReturn(expected);
        assertSame(expected, controller.recommendations().getBody());
    }
}
