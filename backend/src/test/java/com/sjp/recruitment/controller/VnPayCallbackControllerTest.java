package com.sjp.recruitment.controller;

import com.sjp.recruitment.service.BillingService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.view.RedirectView;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VnPayCallbackControllerTest {

    @Mock private BillingService billingService;
    @InjectMocks private VnPayCallbackController controller;

    @Test
    void ipn_returnsSuccessWhenServiceReturnsTrue() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Map<String, String[]> paramMap = new HashMap<>();
        paramMap.put("vnp_ResponseCode", new String[]{"00"});
        paramMap.put("vnp_TxnRef", new String[]{"txn-1"});
        when(request.getParameterMap()).thenReturn(paramMap);
        when(billingService.handleVnPayIpn(anyMap())).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.ipn(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("00", response.getBody().get("RspCode"));
        assertEquals("Confirm Success", response.getBody().get("Message"));
    }

    @Test
    void ipn_returnsFailWhenServiceReturnsFalse() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameterMap()).thenReturn(Map.of("vnp_ResponseCode", new String[]{"97"}));
        when(billingService.handleVnPayIpn(anyMap())).thenReturn(false);

        ResponseEntity<Map<String, String>> response = controller.ipn(request);

        assertEquals("97", response.getBody().get("RspCode"));
        assertEquals("Confirm Fail", response.getBody().get("Message"));
    }

    @Test
    void returnUrl_redirectsToFrontendPaymentResult() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameterMap()).thenReturn(Map.of("vnp_TxnRef", new String[]{"txn-1"}));
        when(request.getParameter("paymentId")).thenReturn("pay-1");
        when(billingService.buildFrontendPaymentResultUrl("pay-1")).thenReturn("http://frontend/payment/pay-1");

        RedirectView result = controller.returnUrl(request);

        verify(billingService).handleVnPayReturn(anyMap(), eq("pay-1"));
        assertEquals("http://frontend/payment/pay-1", result.getUrl());
    }
}
