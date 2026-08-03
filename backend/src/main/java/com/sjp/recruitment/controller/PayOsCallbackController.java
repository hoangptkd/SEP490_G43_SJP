package com.sjp.recruitment.controller;

import com.sjp.recruitment.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/payments/payos")
@RequiredArgsConstructor
public class PayOsCallbackController {

    private final BillingService billingService;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody Map<String, Object> payload) {
        billingService.handlePayOsWebhook(payload);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
