package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.response.PublicSettingsResponse;
import com.sjp.recruitment.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class PublicSettingsController {

    private final SystemSettingsService systemSettingsService;

    @GetMapping("/public")
    public ResponseEntity<PublicSettingsResponse> publicSettings() {
        return ResponseEntity.ok(PublicSettingsResponse.from(systemSettingsService.publicSnapshot()));
    }
}
