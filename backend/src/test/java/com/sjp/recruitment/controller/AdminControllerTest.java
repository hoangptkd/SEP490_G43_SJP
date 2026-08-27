package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.CompanyReviewRequest;
import com.sjp.recruitment.model.dto.response.AdminCompanyDetailResponse;
import com.sjp.recruitment.model.dto.response.AdminCompanySummaryResponse;
import com.sjp.recruitment.service.AdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock private AdminService adminService;
    @InjectMocks private AdminController controller;

    @Test
    void listCompanies_delegatesWithDefaultStatus() {
        List<AdminCompanySummaryResponse> expected = List.of(mock(AdminCompanySummaryResponse.class));
        when(adminService.listCompanies("pending")).thenReturn(expected);
        assertSame(expected, controller.listCompanies("pending").getBody());
    }

    @Test
    void getCompany_delegatesById() {
        AdminCompanyDetailResponse expected = mock(AdminCompanyDetailResponse.class);
        when(adminService.getCompanyDetail("c-1")).thenReturn(expected);
        assertSame(expected, controller.getCompany("c-1").getBody());
    }

    @Test
    void approveCompany_delegatesById() {
        AdminCompanyDetailResponse expected = mock(AdminCompanyDetailResponse.class);
        when(adminService.approveCompany("c-1")).thenReturn(expected);
        assertSame(expected, controller.approveCompany("c-1").getBody());
    }

    @Test
    void rejectCompany_delegatesWithRequest() {
        CompanyReviewRequest request = mock(CompanyReviewRequest.class);
        AdminCompanyDetailResponse expected = mock(AdminCompanyDetailResponse.class);
        when(adminService.rejectCompany("c-1", request)).thenReturn(expected);
        assertSame(expected, controller.rejectCompany("c-1", request).getBody());
    }

    @Test
    void approveCompanyDocument_delegatesToService() {
        AdminCompanyDetailResponse expected = mock(AdminCompanyDetailResponse.class);
        when(adminService.approveCompanyDocument("c-1", "doc-1")).thenReturn(expected);
        assertSame(expected, controller.approveCompanyDocument("c-1", "doc-1").getBody());
    }

    @Test
    void rejectCompanyDocument_delegatesToService() {
        CompanyReviewRequest request = mock(CompanyReviewRequest.class);
        AdminCompanyDetailResponse expected = mock(AdminCompanyDetailResponse.class);
        when(adminService.rejectCompanyDocument("c-1", "doc-1", request)).thenReturn(expected);
        assertSame(expected, controller.rejectCompanyDocument("c-1", "doc-1", request).getBody());
    }
}
