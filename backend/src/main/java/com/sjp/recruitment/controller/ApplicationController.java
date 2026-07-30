package com.sjp.recruitment.controller;

import com.sjp.recruitment.service.ApplicationService;
import com.sjp.recruitment.model.dto.request.ApplicationSubmitRequest;
import com.sjp.recruitment.model.dto.response.ApplicationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CANDIDATE')")
public class ApplicationController {

    private final ApplicationService applicationService;

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
