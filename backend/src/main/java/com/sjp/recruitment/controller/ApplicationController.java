package com.sjp.recruitment.controller;

import com.sjp.recruitment.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.sjp.recruitment.model.entity.Application;

@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping("/candidate/{candidateId}")
    public ResponseEntity<Page<Application>> getByCandidate(
            @PathVariable Long candidateId,
            Pageable pageable) {
        Page<Application> applications = applicationService.findByCandidateId(candidateId, pageable);
        return ResponseEntity.ok(applications);
    }

    @PostMapping("/apply")
    public ResponseEntity<Application> apply(
            @RequestParam Long candidateId,
            @RequestParam Long jobId) {
        Application application = applicationService.apply(candidateId, jobId);
        return ResponseEntity.ok(application);
    }
}