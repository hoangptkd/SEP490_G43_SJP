package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.CompanyReviewRequest;
import com.sjp.recruitment.model.dto.response.AdminCompanyDetailResponse;
import com.sjp.recruitment.model.dto.response.AdminCompanySummaryResponse;
import com.sjp.recruitment.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/companies")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<List<AdminCompanySummaryResponse>> listCompanies(
            @RequestParam(required = false, defaultValue = "pending") String status
    ) {
        return ResponseEntity.ok(adminService.listCompanies(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminCompanyDetailResponse> getCompany(@PathVariable String id) {
        return ResponseEntity.ok(adminService.getCompanyDetail(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AdminCompanyDetailResponse> approveCompany(@PathVariable String id) {
        return ResponseEntity.ok(adminService.approveCompany(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<AdminCompanyDetailResponse> rejectCompany(
            @PathVariable String id,
            @RequestBody CompanyReviewRequest request
    ) {
        return ResponseEntity.ok(adminService.rejectCompany(id, request));
    }

    @PostMapping("/{id}/documents/{documentId}/approve")
    public ResponseEntity<AdminCompanyDetailResponse> approveCompanyDocument(
            @PathVariable String id,
            @PathVariable String documentId
    ) {
        return ResponseEntity.ok(adminService.approveCompanyDocument(id, documentId));
    }

    @PostMapping("/{id}/documents/{documentId}/reject")
    public ResponseEntity<AdminCompanyDetailResponse> rejectCompanyDocument(
            @PathVariable String id,
            @PathVariable String documentId,
            @RequestBody CompanyReviewRequest request
    ) {
        return ResponseEntity.ok(adminService.rejectCompanyDocument(id, documentId, request));
    }
}
