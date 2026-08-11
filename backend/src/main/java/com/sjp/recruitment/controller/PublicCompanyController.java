package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.response.PublicCompanyResponse;
import com.sjp.recruitment.service.PublicCompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
public class PublicCompanyController {
    private final PublicCompanyService publicCompanyService;

    @GetMapping("/{id}")
    public ResponseEntity<PublicCompanyResponse> getCompany(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(publicCompanyService.getCompany(id, page, size));
    }
}
