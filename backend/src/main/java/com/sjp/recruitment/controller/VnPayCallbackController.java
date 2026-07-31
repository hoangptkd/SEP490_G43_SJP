package com.sjp.recruitment.controller;

import com.sjp.recruitment.service.BillingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/payments/vnpay")
@RequiredArgsConstructor
public class VnPayCallbackController {

    private final BillingService billingService;

    @GetMapping("/ipn")
    public ResponseEntity<Map<String, String>> ipn(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);
        boolean ok = billingService.handleVnPayIpn(params);
        Map<String, String> body = new HashMap<>();
        body.put("RspCode", ok ? "00" : "97");
        body.put("Message", ok ? "Confirm Success" : "Confirm Fail");
        return ResponseEntity.ok(body);
    }

    @GetMapping("/return")
    public RedirectView returnUrl(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);
        String paymentId = request.getParameter("paymentId");
        billingService.handleVnPayReturn(params, paymentId);
        String redirect = billingService.buildFrontendPaymentResultUrl(
                paymentId != null ? paymentId : params.get("vnp_TxnRef"));
        return new RedirectView(redirect);
    }

    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }
}
