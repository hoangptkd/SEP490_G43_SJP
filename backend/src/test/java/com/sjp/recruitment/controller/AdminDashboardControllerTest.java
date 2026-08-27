package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.response.AdminDashboardResponse;
import com.sjp.recruitment.model.dto.response.AdminStatisticsResponse;
import com.sjp.recruitment.service.AdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    @Mock private AdminService adminService;
    @InjectMocks private AdminDashboardController controller;

    @Test
    void getDashboard_delegatesToService() {
        AdminDashboardResponse expected = mock(AdminDashboardResponse.class);
        when(adminService.getDashboardStats()).thenReturn(expected);
        assertSame(expected, controller.getDashboard().getBody());
    }

    @Test
    void getStatistics_delegatesWithParams() {
        AdminStatisticsResponse expected = mock(AdminStatisticsResponse.class);
        when(adminService.getStatistics("monthly", 2025, 6, null)).thenReturn(expected);
        assertSame(expected, controller.getStatistics("monthly", 2025, 6, null).getBody());
    }
}
