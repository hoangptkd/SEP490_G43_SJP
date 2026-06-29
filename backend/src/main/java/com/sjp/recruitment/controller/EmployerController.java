package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.CompanyProfileRequest;
import com.sjp.recruitment.model.dto.response.CompanyProfileResponse;
import com.sjp.recruitment.service.EmployerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
