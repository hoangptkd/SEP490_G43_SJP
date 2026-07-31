package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.CheckoutRequest;
import com.sjp.recruitment.model.dto.response.BankTransferInfo;
import com.sjp.recruitment.model.dto.response.CheckoutResponse;
import com.sjp.recruitment.model.dto.response.PaymentStatusResponse;
import com.sjp.recruitment.model.dto.response.PlanCatalogResponse;
import com.sjp.recruitment.model.dto.response.UserSubscriptionResponse;
import com.sjp.recruitment.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/plans")
    public ResponseEntity<List<PlanCatalogResponse>> listPlans() {
        return ResponseEntity.ok(billingService.listAvailablePlans());
    }

    @GetMapping("/me")
    public ResponseEntity<UserSubscriptionResponse> getMySubscription() {
        return ResponseEntity.ok(billingService.getMySubscription());
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(@RequestBody CheckoutRequest request) {
        return ResponseEntity.ok(billingService.checkout(request));
    }

    @GetMapping("/payments/{id}")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(@PathVariable String id) {
        return ResponseEntity.ok(billingService.getPaymentStatus(id));
    }

    @GetMapping("/payments/{id}/bank-transfer")
    public ResponseEntity<BankTransferInfo> getBankTransfer(@PathVariable String id) {
        return ResponseEntity.ok(billingService.getBankTransferCheckout(id));
    }
}
