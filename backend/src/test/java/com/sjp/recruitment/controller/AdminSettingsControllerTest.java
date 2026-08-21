package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.AdminSettingsUpdateRequest;
import com.sjp.recruitment.model.dto.response.AdminSettingResponse;
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
class AdminSettingsControllerTest {

    @Mock private AdminOpsService adminOpsService;
    @InjectMocks private AdminSettingsController controller;

    @Test
    void list_delegatesToService() {
        List<AdminSettingResponse> expected = List.of(mock(AdminSettingResponse.class));
        when(adminOpsService.listSettings()).thenReturn(expected);
        assertSame(expected, controller.list().getBody());
    }

    @Test
    void update_delegatesToService() {
        AdminSettingsUpdateRequest request = mock(AdminSettingsUpdateRequest.class);
        List<AdminSettingResponse> expected = List.of(mock(AdminSettingResponse.class));
        when(adminOpsService.updateSettings(request)).thenReturn(expected);
        assertSame(expected, controller.update(request).getBody());
    }
}
