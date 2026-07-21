package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.response.AdminAuditLogResponse;
import com.sjp.recruitment.service.AdminOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminOpsService adminOpsService;

    @GetMapping
    public ResponseEntity<List<AdminAuditLogResponse>> list(
            @RequestParam(required = false, defaultValue = "all") String targetType,
            @RequestParam(required = false, defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(adminOpsService.listAuditLogs(targetType, limit));
    }
}
