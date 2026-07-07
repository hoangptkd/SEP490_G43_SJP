package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.CompanyReviewRequest;
import com.sjp.recruitment.model.dto.response.AdminJobDetailResponse;
import com.sjp.recruitment.model.dto.response.AdminJobSummaryResponse;
import com.sjp.recruitment.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/jobs")
@RequiredArgsConstructor
public class AdminJobController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<List<AdminJobSummaryResponse>> listJobs(
            @RequestParam(required = false, defaultValue = "pending_review") String status
    ) {
        return ResponseEntity.ok(adminService.listJobs(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminJobDetailResponse> getJob(@PathVariable String id) {
        return ResponseEntity.ok(adminService.getJobDetail(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AdminJobDetailResponse> approveJob(@PathVariable String id) {
        return ResponseEntity.ok(adminService.approveJob(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<AdminJobDetailResponse> rejectJob(
            @PathVariable String id,
            @RequestBody CompanyReviewRequest request
    ) {
        return ResponseEntity.ok(adminService.rejectJob(id, request));
    }
}
