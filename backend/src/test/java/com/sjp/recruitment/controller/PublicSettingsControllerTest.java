package com.sjp.recruitment.controller;

import com.sjp.recruitment.service.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicSettingsControllerTest {

    @Mock private SystemSettingsService systemSettingsService;
    @InjectMocks private PublicSettingsController controller;

    @Test
    void publicSettings_delegatesToService() {
        Map<String, String> snapshot = Map.of(
                "site_name", "Test Site",
                "support_email", "test@sjp.local"
        );
        when(systemSettingsService.publicSnapshot()).thenReturn(snapshot);

        var response = controller.publicSettings();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Test Site", response.getBody().siteName());
    }
}
