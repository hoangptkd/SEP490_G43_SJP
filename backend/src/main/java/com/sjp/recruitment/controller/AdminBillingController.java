package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.AdminPlanUpdateRequest;
import com.sjp.recruitment.model.dto.request.SubscriptionActionRequest;
import com.sjp.recruitment.model.dto.response.AdminPaymentResponse;
import com.sjp.recruitment.model.dto.response.AdminPlanResponse;
import com.sjp.recruitment.model.dto.response.AdminRevenueSummaryResponse;
import com.sjp.recruitment.model.dto.response.AdminSubscriptionResponse;
import com.sjp.recruitment.service.AdminOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/billing")
@RequiredArgsConstructor
public class AdminBillingController {

    private final AdminOpsService adminOpsService;

    @GetMapping("/plans")
    public ResponseEntity<List<AdminPlanResponse>> listPlans(
            @RequestParam(required = false, defaultValue = "all") String status
    ) {
        return ResponseEntity.ok(adminOpsService.listPlans(status));
    }

    @PostMapping("/plans")
    public ResponseEntity<AdminPlanResponse> createPlan(@RequestBody AdminPlanUpdateRequest request) {
        return ResponseEntity.ok(adminOpsService.createPlan(request));
    }

    @PutMapping("/plans/{id}")
    public ResponseEntity<AdminPlanResponse> updatePlan(
            @PathVariable String id,
            @RequestBody AdminPlanUpdateRequest request
    ) {
        return ResponseEntity.ok(adminOpsService.updatePlan(id, request));
    }

    @DeleteMapping("/plans/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable String id) {
        adminOpsService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<List<AdminSubscriptionResponse>> listSubscriptions(
            @RequestParam(required = false, defaultValue = "all") String status
    ) {
        return ResponseEntity.ok(adminOpsService.listSubscriptions(status));
    }

    @PostMapping("/subscriptions/{id}/cancel")
    public ResponseEntity<AdminSubscriptionResponse> cancelSubscription(
            @PathVariable String id,
            @RequestBody(required = false) SubscriptionActionRequest request
    ) {
        return ResponseEntity.ok(adminOpsService.cancelSubscription(id, request));
    }

    @PostMapping("/subscriptions/{id}/activate")
    public ResponseEntity<AdminSubscriptionResponse> activateSubscription(@PathVariable String id) {
        return ResponseEntity.ok(adminOpsService.activateSubscription(id));
    }

    @GetMapping("/payments")
    public ResponseEntity<List<AdminPaymentResponse>> listPayments(
            @RequestParam(required = false, defaultValue = "all") String status
    ) {
        return ResponseEntity.ok(adminOpsService.listPayments(status));
    }

    @GetMapping("/revenue")
    public ResponseEntity<AdminRevenueSummaryResponse> getRevenue() {
        return ResponseEntity.ok(adminOpsService.getRevenueSummary());
    }
}
