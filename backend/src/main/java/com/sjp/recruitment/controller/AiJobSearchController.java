package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.AiJobSearchConsentRequest;
import com.sjp.recruitment.model.dto.request.AiJobSearchRequest;
import com.sjp.recruitment.model.dto.response.AiJobSearchResponse;
import com.sjp.recruitment.model.dto.response.AiJobSearchStatusResponse;
import com.sjp.recruitment.service.AiJobSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/candidate/ai-job-search")
@PreAuthorize("hasRole('CANDIDATE')")
@RequiredArgsConstructor
public class AiJobSearchController {
    private final AiJobSearchService aiJobSearchService;

    @GetMapping("/status")
    public ResponseEntity<AiJobSearchStatusResponse> status() {
        return ResponseEntity.ok(aiJobSearchService.status());
    }

    @PostMapping("/consent")
    public ResponseEntity<AiJobSearchStatusResponse> consent(@Valid @RequestBody AiJobSearchConsentRequest request) {
        return ResponseEntity.ok(aiJobSearchService.consent(request));
    }

    @DeleteMapping("/consent")
    public ResponseEntity<Void> revokeConsent() {
        aiJobSearchService.revokeConsent();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/search")
    public ResponseEntity<AiJobSearchResponse> search(@RequestBody(required = false) AiJobSearchRequest request) {
        return ResponseEntity.ok(aiJobSearchService.search(request != null && request.forceRefresh()));
    }
}
