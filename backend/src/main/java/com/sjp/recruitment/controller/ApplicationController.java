package com.sjp.recruitment.controller;

import com.sjp.recruitment.service.ApplicationService;
import com.sjp.recruitment.model.dto.request.ApplicationSubmitRequest;
import com.sjp.recruitment.model.dto.response.ApplicationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.sjp.recruitment.model.entity.Application;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping("/candidate/{candidateId}")
    public ResponseEntity<Page<Application>> getByCandidate(
            @PathVariable String candidateId,
            Pageable pageable) {
        Page<Application> applications = applicationService.findByCandidateId(candidateId, pageable);
        return ResponseEntity.ok(applications);
    }

    @PostMapping("/apply")
    public ResponseEntity<Application> apply(
            @RequestParam String candidateId,
            @RequestParam String jobId) {
        Application application = applicationService.apply(candidateId, jobId);
        return ResponseEntity.ok(application);
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> submit(@Valid @RequestBody ApplicationSubmitRequest request) {
        return ResponseEntity.ok(applicationService.submit(request));
    }

    @GetMapping("/me")
    public ResponseEntity<List<ApplicationResponse>> myApplications() {
        return ResponseEntity.ok(applicationService.myApplications());
    }

    @GetMapping("/me/{id}")
    public ResponseEntity<ApplicationResponse> myApplication(@PathVariable String id) {
        return ResponseEntity.ok(applicationService.myApplication(id));
    }
}
