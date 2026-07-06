package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.CompanyLocationRequest;
import com.sjp.recruitment.model.dto.request.CompanyProfileRequest;
import com.sjp.recruitment.model.dto.response.CompanyDocumentResponse;
import com.sjp.recruitment.model.dto.response.CompanyLocationResponse;
import com.sjp.recruitment.model.dto.response.CompanyProfileResponse;
import com.sjp.recruitment.model.dto.request.JobRequest;
import com.sjp.recruitment.model.dto.response.JobResponse;
import com.sjp.recruitment.service.EmployerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/employer")
@RequiredArgsConstructor
public class EmployerController {

    private final EmployerService employerService;

    @GetMapping("/company")
    public ResponseEntity<CompanyProfileResponse> getCompanyProfile() {
        return ResponseEntity.ok(employerService.getCompanyProfile());
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
    public ResponseEntity<List<JobResponse>> getCompanyJobs() {
        return ResponseEntity.ok(employerService.getCompanyJobs());
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
}
