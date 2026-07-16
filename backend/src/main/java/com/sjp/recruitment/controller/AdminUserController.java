package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.response.AdminUserSummaryResponse;
import com.sjp.recruitment.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<List<AdminUserSummaryResponse>> listUsers(
            @RequestParam(required = false, defaultValue = "all") String role,
            @RequestParam(required = false, defaultValue = "all") String status
    ) {
        return ResponseEntity.ok(adminService.listUsers(role, status));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<AdminUserSummaryResponse> suspendUser(@PathVariable String id) {
        return ResponseEntity.ok(adminService.suspendUser(id));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<AdminUserSummaryResponse> activateUser(@PathVariable String id) {
        return ResponseEntity.ok(adminService.activateUser(id));
    }
}
