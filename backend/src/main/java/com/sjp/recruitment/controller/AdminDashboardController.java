package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.response.AdminDashboardResponse;
import com.sjp.recruitment.model.dto.response.AdminStatisticsResponse;
import com.sjp.recruitment.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @GetMapping("/statistics")
    public ResponseEntity<AdminStatisticsResponse> getStatistics(
            @RequestParam(required = false, defaultValue = "week") String period,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String date
    ) {
        return ResponseEntity.ok(adminService.getStatistics(period, year, month, date));
    }
}
