package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.CompanyReviewRequest;
import com.sjp.recruitment.model.dto.request.JobReportResolveRequest;
import com.sjp.recruitment.model.dto.response.AdminJobDetailResponse;
import com.sjp.recruitment.model.dto.response.AdminJobReportResponse;
import com.sjp.recruitment.model.dto.response.AdminJobSummaryResponse;
import com.sjp.recruitment.service.AdminService;
import com.sjp.recruitment.service.JobReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminJobControllerTest {

    @Mock private AdminService adminService;
    @Mock private JobReportService jobReportService;
    @InjectMocks private AdminJobController controller;

    @Test
    void listJobs_delegatesWithDefaultStatus() {
        List<AdminJobSummaryResponse> expected = List.of(mock(AdminJobSummaryResponse.class));
        when(adminService.listJobs("pending_review")).thenReturn(expected);
        assertSame(expected, controller.listJobs("pending_review").getBody());
    }

    @Test
    void listJobReports_delegatesToJobReportService() {
        List<AdminJobReportResponse> expected = List.of(mock(AdminJobReportResponse.class));
        when(jobReportService.listReports("pending")).thenReturn(expected);
        assertSame(expected, controller.listJobReports("pending").getBody());
    }

    @Test
    void dismissJobReport_delegatesToService() {
        JobReportResolveRequest request = mock(JobReportResolveRequest.class);
        AdminJobReportResponse expected = mock(AdminJobReportResponse.class);
        when(jobReportService.dismissReport("r-1", request)).thenReturn(expected);
        assertSame(expected, controller.dismissJobReport("r-1", request).getBody());
    }

    @Test
    void notifyCompany_delegatesToService() {
        JobReportResolveRequest request = mock(JobReportResolveRequest.class);
        AdminJobReportResponse expected = mock(AdminJobReportResponse.class);
        when(jobReportService.notifyCompany("r-1", request)).thenReturn(expected);
        assertSame(expected, controller.notifyCompany("r-1", request).getBody());
    }

    @Test
    void getJob_delegatesToService() {
        AdminJobDetailResponse expected = mock(AdminJobDetailResponse.class);
        when(adminService.getJobDetail("j-1")).thenReturn(expected);
        assertSame(expected, controller.getJob("j-1").getBody());
    }

    @Test
    void approveJob_delegatesToService() {
        AdminJobDetailResponse expected = mock(AdminJobDetailResponse.class);
        when(adminService.approveJob("j-1")).thenReturn(expected);
        assertSame(expected, controller.approveJob("j-1").getBody());
    }

    @Test
    void rejectJob_delegatesToService() {
        CompanyReviewRequest request = mock(CompanyReviewRequest.class);
        AdminJobDetailResponse expected = mock(AdminJobDetailResponse.class);
        when(adminService.rejectJob("j-1", request)).thenReturn(expected);
        assertSame(expected, controller.rejectJob("j-1", request).getBody());
    }

    @Test
    void closeJob_delegatesToService() {
        CompanyReviewRequest request = mock(CompanyReviewRequest.class);
        AdminJobDetailResponse expected = mock(AdminJobDetailResponse.class);
        when(adminService.closeJob("j-1", request)).thenReturn(expected);
        assertSame(expected, controller.closeJob("j-1", request).getBody());
    }

    @Test
    void reopenJob_delegatesToService() {
        AdminJobDetailResponse expected = mock(AdminJobDetailResponse.class);
        when(adminService.reopenJob("j-1")).thenReturn(expected);
        assertSame(expected, controller.reopenJob("j-1").getBody());
    }
}
