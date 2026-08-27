package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.AdminSettingsUpdateRequest;
import com.sjp.recruitment.model.dto.response.AdminSettingResponse;
import com.sjp.recruitment.service.AdminOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final AdminOpsService adminOpsService;

    @GetMapping
    public ResponseEntity<List<AdminSettingResponse>> list() {
        return ResponseEntity.ok(adminOpsService.listSettings());
    }

    @PutMapping
    public ResponseEntity<List<AdminSettingResponse>> update(@RequestBody AdminSettingsUpdateRequest request) {
        return ResponseEntity.ok(adminOpsService.updateSettings(request));
    }
}
