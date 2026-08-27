package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.response.AdminAuditLogResponse;
import com.sjp.recruitment.service.AdminOpsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuditControllerTest {

    @Mock private AdminOpsService adminOpsService;
    @InjectMocks private AdminAuditController controller;

    @Test
    void list_delegatesWithDefaults() {
        List<AdminAuditLogResponse> expected = List.of(mock(AdminAuditLogResponse.class));
        when(adminOpsService.listAuditLogs("all", 100)).thenReturn(expected);
        assertSame(expected, controller.list("all", 100).getBody());
    }

    @Test
    void list_delegatesWithCustomParams() {
        List<AdminAuditLogResponse> expected = List.of();
        when(adminOpsService.listAuditLogs("company", 50)).thenReturn(expected);
        assertSame(expected, controller.list("company", 50).getBody());
    }
}
