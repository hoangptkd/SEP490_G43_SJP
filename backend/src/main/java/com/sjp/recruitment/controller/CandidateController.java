package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.CandidateProfileRequest;
import com.sjp.recruitment.model.dto.request.CandidateOnboardingRequest;
import com.sjp.recruitment.model.dto.request.CvVersionRequest;
import com.sjp.recruitment.model.dto.request.JobAlertRequest;
import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.service.CandidateService;
import com.sjp.recruitment.service.JobService;
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

@RestController
@RequestMapping("/candidate")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;
    private final JobService jobService;
    private final com.sjp.recruitment.service.JobAlertService jobAlertService;

    @GetMapping("/profile")
    public ResponseEntity<CandidateProfileResponse> getProfile() {
        return ResponseEntity.ok(candidateService.getProfile());
    }

    @PutMapping("/profile")
    public ResponseEntity<CandidateProfileResponse> updateProfile(@Valid @RequestBody CandidateProfileRequest request) {
        return ResponseEntity.ok(candidateService.updateProfile(request));
    }

    @GetMapping("/onboarding")
    public ResponseEntity<CandidateOnboardingResponse> getOnboarding() {
        return ResponseEntity.ok(candidateService.getOnboarding());
    }

    @PutMapping("/onboarding")
    public ResponseEntity<CandidateOnboardingResponse> completeOnboarding(
            @Valid @RequestBody CandidateOnboardingRequest request) {
        return ResponseEntity.ok(candidateService.completeOnboarding(request));
    }

    @PostMapping("/onboarding/skip")
    public ResponseEntity<CandidateOnboardingResponse> skipOnboarding() {
        return ResponseEntity.ok(candidateService.skipOnboarding());
    }

    @GetMapping("/onboarding/job-title-suggestions")
    public ResponseEntity<List<String>> jobTitleSuggestions(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(candidateService.jobTitleSuggestions(query, size));
    }

    @GetMapping("/cvs")
    public ResponseEntity<PageResponse<CvResponse>> getCvs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(candidateService.getCvs(page, size));
    }

    @PostMapping(value = "/cvs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CvResponse> uploadCv(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(candidateService.uploadCv(file));
    }

    @PatchMapping("/cvs/{id}/default")
    public ResponseEntity<CvResponse> setDefaultCv(@PathVariable String id) {
        return ResponseEntity.ok(candidateService.setDefaultCv(id));
    }

    @DeleteMapping("/cvs/{id}")
    public ResponseEntity<Void> deleteCv(@PathVariable String id) {
        candidateService.deleteCv(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cvs/{id}/download")
    public ResponseEntity<Resource> downloadCv(@PathVariable String id) {
        CandidateService.CvDownload download = candidateService.downloadCv(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(download.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(download.resource());
    }

    @GetMapping("/cv-versions")
    public ResponseEntity<PageResponse<CvVersionResponse>> getCvVersions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(candidateService.getCvVersions(page, size));
    }

    @PostMapping("/cv-versions")
    public ResponseEntity<CvVersionResponse> createCvVersion(@Valid @RequestBody CvVersionRequest request) {
        return ResponseEntity.ok(candidateService.createCvVersion(request));
    }

    @PutMapping("/cv-versions/{id}")
    public ResponseEntity<CvVersionResponse> updateCvVersion(@PathVariable String id, @Valid @RequestBody CvVersionRequest request) {
        return ResponseEntity.ok(candidateService.updateCvVersion(id, request));
    }

    @DeleteMapping("/cv-versions/{id}")
    public ResponseEntity<Void> deleteCvVersion(@PathVariable String id) {
        candidateService.deleteCvVersion(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/saved-jobs")
    public ResponseEntity<PageResponse<JobResponse>> savedJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(candidateService.getSavedJobs(page, size));
    }

    @PostMapping("/saved-jobs/{jobId}")
    public ResponseEntity<Void> saveJob(@PathVariable String jobId) {
        candidateService.saveJob(jobId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/saved-jobs/{jobId}")
    public ResponseEntity<Void> unsaveJob(@PathVariable String jobId) {
        candidateService.unsaveJob(jobId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/recommendations/jobs")
    public ResponseEntity<List<RecommendationResponse>> recommendations() {
        return ResponseEntity.ok(jobService.recommendations());
    }

    @GetMapping("/notifications")
    public ResponseEntity<PageResponse<NotificationResponse>> notifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(candidateService.getNotifications(page, size));
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markNotificationRead(@PathVariable String id) {
        candidateService.markNotificationRead(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllNotificationsRead() {
        candidateService.markAllNotificationsRead();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionResponse> subscription() {
        return ResponseEntity.ok(candidateService.getSubscription());
    }

    @GetMapping("/job-alerts")
    public ResponseEntity<PageResponse<JobAlertResponse>> jobAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(jobAlertService.list(page, size));
    }

    @PostMapping("/job-alerts")
    public ResponseEntity<JobAlertResponse> createJobAlert(@Valid @RequestBody JobAlertRequest request) {
        return ResponseEntity.ok(jobAlertService.create(request));
    }

    @PutMapping("/job-alerts/{id}")
    public ResponseEntity<JobAlertResponse> updateJobAlert(@PathVariable String id, @Valid @RequestBody JobAlertRequest request) {
        return ResponseEntity.ok(jobAlertService.update(id, request));
    }

    @DeleteMapping("/job-alerts/{id}")
    public ResponseEntity<Void> deleteJobAlert(@PathVariable String id) {
        jobAlertService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
