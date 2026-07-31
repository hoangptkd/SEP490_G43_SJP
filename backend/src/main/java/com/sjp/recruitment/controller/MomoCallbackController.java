package com.sjp.recruitment.controller;

import com.sjp.recruitment.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payments/momo")
@RequiredArgsConstructor
public class MomoCallbackController {

    private final BillingService billingService;

    @PostMapping("/ipn")
    public ResponseEntity<Map<String, Object>> ipn(@RequestBody Map<String, Object> payload) {
        billingService.handleMomoIpn(payload);
        return ResponseEntity.ok(Map.of(
                "partnerCode", payload.getOrDefault("partnerCode", ""),
                "requestId", payload.getOrDefault("requestId", ""),
                "orderId", payload.getOrDefault("orderId", ""),
                "resultCode", 0,
                "message", "Success"
        ));
    }
}
