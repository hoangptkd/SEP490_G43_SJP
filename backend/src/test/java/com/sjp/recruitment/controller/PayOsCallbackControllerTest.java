package com.sjp.recruitment.controller;

import com.sjp.recruitment.service.BillingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayOsCallbackControllerTest {

    @Mock private BillingService billingService;
    @InjectMocks private PayOsCallbackController controller;

    @Test
    void webhook_delegatesToServiceAndReturnsSuccess() {
        Map<String, Object> payload = Map.of("code", "00", "id", "txn-1");

        ResponseEntity<Map<String, Object>> response = controller.webhook(payload);

        verify(billingService).handlePayOsWebhook(payload);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
    }
}
