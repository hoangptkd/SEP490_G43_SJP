package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.CandidateProfileRequest;
import com.sjp.recruitment.model.dto.request.CvVersionRequest;
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

    @GetMapping("/profile")
    public ResponseEntity<CandidateProfileResponse> getProfile() {
        return ResponseEntity.ok(candidateService.getProfile());
    }

    @PutMapping("/profile")
    public ResponseEntity<CandidateProfileResponse> updateProfile(@Valid @RequestBody CandidateProfileRequest request) {
        return ResponseEntity.ok(candidateService.updateProfile(request));
    }

    @GetMapping("/cvs")
    public ResponseEntity<List<CvResponse>> getCvs() {
        return ResponseEntity.ok(candidateService.getCvs());
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
    public ResponseEntity<List<CvVersionResponse>> getCvVersions() {
        return ResponseEntity.ok(candidateService.getCvVersions());
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
    public ResponseEntity<List<JobResponse>> savedJobs() {
        return ResponseEntity.ok(candidateService.getSavedJobs());
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
    public ResponseEntity<List<NotificationResponse>> notifications() {
        return ResponseEntity.ok(candidateService.getNotifications());
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
}
