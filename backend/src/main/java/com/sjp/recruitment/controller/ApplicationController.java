package com.sjp.recruitment.controller;

import com.sjp.recruitment.service.ApplicationService;
import com.sjp.recruitment.model.dto.request.ApplicationSubmitRequest;
import com.sjp.recruitment.model.dto.response.ApplicationResponse;
import com.sjp.recruitment.model.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;

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
    public ResponseEntity<PageResponse<ApplicationResponse>> myApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(applicationService.myApplications(page, size));
    }

    @GetMapping("/me/{id}")
    public ResponseEntity<ApplicationResponse> myApplication(@PathVariable String id) {
        return ResponseEntity.ok(applicationService.myApplication(id));
    }

    @GetMapping("/me/{id}/resume")
    public ResponseEntity<Resource> downloadSubmittedResume(@PathVariable String id) {
        com.sjp.recruitment.service.CandidateService.CvDownload download = applicationService.downloadMySubmittedCv(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(download.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(download.resource());
    }
}
