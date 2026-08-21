package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.response.AdminUserSummaryResponse;
import com.sjp.recruitment.service.AdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock private AdminService adminService;
    @InjectMocks private AdminUserController controller;

    @Test
    void listUsers_delegatesWithFilters() {
        List<AdminUserSummaryResponse> expected = List.of(mock(AdminUserSummaryResponse.class));
        when(adminService.listUsers("all", "all")).thenReturn(expected);
        assertSame(expected, controller.listUsers("all", "all").getBody());
    }

    @Test
    void suspendUser_delegatesToService() {
        AdminUserSummaryResponse expected = mock(AdminUserSummaryResponse.class);
        when(adminService.suspendUser("u-1")).thenReturn(expected);
        assertSame(expected, controller.suspendUser("u-1").getBody());
    }

    @Test
    void activateUser_delegatesToService() {
        AdminUserSummaryResponse expected = mock(AdminUserSummaryResponse.class);
        when(adminService.activateUser("u-1")).thenReturn(expected);
        assertSame(expected, controller.activateUser("u-1").getBody());
    }
}
