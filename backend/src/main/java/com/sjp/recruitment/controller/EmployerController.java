package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.CompanyLocationRequest;
import com.sjp.recruitment.model.dto.request.CompanyProfileRequest;
import com.sjp.recruitment.model.dto.request.EmployerPersonalProfileRequest;
import com.sjp.recruitment.model.dto.response.CompanyDocumentResponse;
import com.sjp.recruitment.model.dto.response.CompanyLocationResponse;
import com.sjp.recruitment.model.dto.response.CompanyProfileResponse;
import com.sjp.recruitment.model.dto.response.EmployerDashboardResponse;
import com.sjp.recruitment.model.dto.request.JobRequest;
import com.sjp.recruitment.model.dto.response.JobResponse;
import com.sjp.recruitment.model.dto.response.ApplicationResponse;
import com.sjp.recruitment.model.dto.response.MessageResponse;
import com.sjp.recruitment.model.dto.response.NotificationResponse;
import com.sjp.recruitment.service.CandidateService;
import com.sjp.recruitment.service.EmployerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/employer")
@RequiredArgsConstructor
public class EmployerController {

    private final EmployerService employerService;

    @GetMapping("/dashboard")
    public ResponseEntity<EmployerDashboardResponse> getDashboardStats() {
        return ResponseEntity.ok(employerService.getDashboardStats());
    }

    @GetMapping("/company")
    public ResponseEntity<CompanyProfileResponse> getCompanyProfile() {
        return ResponseEntity.ok(employerService.getCompanyProfile());
    }

    @PutMapping("/profile/personal")
    public ResponseEntity<MessageResponse> updatePersonalProfile(@Valid @RequestBody EmployerPersonalProfileRequest request) {
        employerService.updatePersonalProfile(request);
        return ResponseEntity.ok(new MessageResponse("Cập nhật thông tin cá nhân thành công"));
    }

    @PutMapping("/company")
    public ResponseEntity<CompanyProfileResponse> updateCompanyProfile(@Valid @RequestBody CompanyProfileRequest request) {
        return ResponseEntity.ok(employerService.updateCompanyProfile(request));
    }

    @GetMapping("/company/locations")
    public ResponseEntity<List<CompanyLocationResponse>> getCompanyLocations() {
        return ResponseEntity.ok(employerService.getCompanyLocations());
    }

    @PostMapping("/company/locations")
    public ResponseEntity<CompanyLocationResponse> createCompanyLocation(@Valid @RequestBody CompanyLocationRequest request) {
        return ResponseEntity.ok(employerService.createCompanyLocation(request));
    }

    @PutMapping("/company/locations/{id}")
    public ResponseEntity<CompanyLocationResponse> updateCompanyLocation(@PathVariable String id, @Valid @RequestBody CompanyLocationRequest request) {
        return ResponseEntity.ok(employerService.updateCompanyLocation(id, request));
    }

    @DeleteMapping("/company/locations/{id}")
    public ResponseEntity<Void> deleteCompanyLocation(@PathVariable String id) {
        employerService.deleteCompanyLocation(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/company/documents")
    public ResponseEntity<List<CompanyDocumentResponse>> getCompanyDocuments() {
        return ResponseEntity.ok(employerService.getCompanyDocuments());
    }

    @PostMapping(value = "/company/documents", consumes = "multipart/form-data")
    public ResponseEntity<CompanyDocumentResponse> uploadCompanyDocument(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(employerService.uploadCompanyDocument(file));
    }

    @PostMapping(value = "/company/documents/{id}/replace", consumes = "multipart/form-data")
    public ResponseEntity<CompanyDocumentResponse> replaceCompanyDocument(
            @PathVariable String id,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(employerService.replaceCompanyDocument(id, file));
    }

    @DeleteMapping("/company/documents/{id}")
    public ResponseEntity<Void> deleteCompanyDocument(@PathVariable String id) {
        employerService.deleteCompanyDocument(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/company/logo", consumes = "multipart/form-data")
    public ResponseEntity<CompanyProfileResponse> uploadCompanyLogo(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(employerService.uploadCompanyLogo(file));
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<JobResponse>> getCompanyJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(employerService.getCompanyJobs(status, search));
    }

    @PostMapping("/jobs")
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody JobRequest request) {
        return ResponseEntity.ok(employerService.createJob(request));
    }

    @PutMapping("/jobs/{id}")
    public ResponseEntity<JobResponse> updateJob(@PathVariable String id, @Valid @RequestBody JobRequest request) {
        return ResponseEntity.ok(employerService.updateJob(id, request));
    }

    @PostMapping("/jobs/{id}/submit-review")
    public ResponseEntity<JobResponse> submitJobForReview(@PathVariable String id) {
        return ResponseEntity.ok(employerService.submitJobForReview(id));
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable String id) {
        employerService.deleteJob(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/jobs/{id}/close")
    public ResponseEntity<JobResponse> closeJob(@PathVariable String id) {
        return ResponseEntity.ok(employerService.closeJob(id));
    }

    @PostMapping("/jobs/{id}/reopen")
    public ResponseEntity<JobResponse> reopenJob(@PathVariable String id,
                                                 @RequestParam(required = false) String deadline,
                                                 @RequestBody(required = false) Map<String, String> body) {
        String targetDeadline = body != null && body.get("deadline") != null ? body.get("deadline") : deadline;
        return ResponseEntity.ok(employerService.reopenJob(id, targetDeadline));
    }

    @GetMapping("/applications")
    public ResponseEntity<List<ApplicationResponse>> getCompanyApplications(
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(employerService.getCompanyApplications(jobId, status, search));
    }

    @GetMapping("/jobs/{jobId}/applications")
    public ResponseEntity<List<ApplicationResponse>> getJobApplications(
            @PathVariable String jobId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(employerService.getCompanyApplications(jobId, status, search));
    }

    @PutMapping("/applications/{id}/status")
    public ResponseEntity<ApplicationResponse> updateApplicationStatus(
            @PathVariable String id,
            @RequestParam(required = false) String status,
            @RequestBody(required = false) Map<String, String> body) {
        String targetStatus = body != null && body.get("status") != null ? body.get("status") : status;
        String note = body != null ? body.get("note") : null;
        return ResponseEntity.ok(employerService.updateApplicationStatus(id, targetStatus, note));
    }

    @GetMapping("/applications/{id}/cv")
    public ResponseEntity<Resource> downloadApplicationCv(@PathVariable String id) {
        CandidateService.CvDownload download = employerService.downloadApplicationCv(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(download.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(download.resource());
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationResponse>> notifications() {
        return ResponseEntity.ok(employerService.getNotifications());
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markNotificationRead(@PathVariable String id) {
        employerService.markNotificationRead(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllNotificationsRead() {
        employerService.markAllNotificationsRead();
        return ResponseEntity.noContent().build();
    }
}
