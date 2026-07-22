package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.CompanyReviewRequest;
import com.sjp.recruitment.model.dto.request.JobReportResolveRequest;
import com.sjp.recruitment.model.dto.response.AdminJobDetailResponse;
import com.sjp.recruitment.model.dto.response.AdminJobReportResponse;
import com.sjp.recruitment.model.dto.response.AdminJobSummaryResponse;
import com.sjp.recruitment.service.AdminService;
import com.sjp.recruitment.service.JobReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/jobs")
@RequiredArgsConstructor
public class AdminJobController {

    private final AdminService adminService;
    private final JobReportService jobReportService;

    @GetMapping
    public ResponseEntity<List<AdminJobSummaryResponse>> listJobs(
            @RequestParam(required = false, defaultValue = "pending_review") String status
    ) {
        return ResponseEntity.ok(adminService.listJobs(status));
    }

    @GetMapping("/reports")
    public ResponseEntity<List<AdminJobReportResponse>> listJobReports(
            @RequestParam(required = false, defaultValue = "pending") String status
    ) {
        return ResponseEntity.ok(jobReportService.listReports(status));
    }

    @PostMapping("/reports/{reportId}/dismiss")
    public ResponseEntity<AdminJobReportResponse> dismissJobReport(
            @PathVariable String reportId,
            @RequestBody(required = false) JobReportResolveRequest request
    ) {
        return ResponseEntity.ok(jobReportService.dismissReport(reportId, request));
    }

    @PostMapping("/reports/{reportId}/notify-company")
    public ResponseEntity<AdminJobReportResponse> notifyCompany(
            @PathVariable String reportId,
            @RequestBody(required = false) JobReportResolveRequest request
    ) {
        return ResponseEntity.ok(jobReportService.notifyCompany(reportId, request));
    }

    @PostMapping("/reports/{reportId}/resolve")
    public ResponseEntity<AdminJobReportResponse> resolveJobReport(
            @PathVariable String reportId,
            @RequestBody(required = false) JobReportResolveRequest request
    ) {
        return ResponseEntity.ok(jobReportService.resolveReport(reportId, request));
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

    @PostMapping("/{id}/close")
    public ResponseEntity<AdminJobDetailResponse> closeJob(
            @PathVariable String id,
            @RequestBody(required = false) CompanyReviewRequest request
    ) {
        return ResponseEntity.ok(adminService.closeJob(id, request));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<AdminJobDetailResponse> reopenJob(@PathVariable String id) {
        return ResponseEntity.ok(adminService.reopenJob(id));
    }
}
