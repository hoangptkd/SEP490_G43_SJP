package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.service.BillingService;
import jakarta.servlet.http.HttpServletRequest;
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
class MomoCallbackControllerTest {

    @Mock private BillingService billingService;
    @InjectMocks private MomoCallbackController controller;

    @Test
    void ipn_delegatesToServiceAndReturnsSuccessShape() {
        Map<String, Object> payload = Map.of(
                "partnerCode", "MOMO",
                "requestId", "req-1",
                "orderId", "order-1",
                "resultCode", 0
        );

        ResponseEntity<Map<String, Object>> response = controller.ipn(payload);

        verify(billingService).handleMomoIpn(payload);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().get("resultCode"));
        assertEquals("Success", response.getBody().get("message"));
        assertEquals("MOMO", response.getBody().get("partnerCode"));
    }
}
