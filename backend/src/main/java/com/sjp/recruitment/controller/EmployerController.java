package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.CompanyLocationRequest;
import com.sjp.recruitment.model.dto.request.CompanyProfileRequest;
import com.sjp.recruitment.model.dto.response.CompanyLocationResponse;
import com.sjp.recruitment.model.dto.response.CompanyProfileResponse;
import com.sjp.recruitment.service.EmployerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
