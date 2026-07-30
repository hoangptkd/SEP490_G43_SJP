package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.AdminCategoryRequest;
import com.sjp.recruitment.model.dto.response.AdminCategoryResponse;
import com.sjp.recruitment.service.AdminOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final AdminOpsService adminOpsService;

    @GetMapping
    public ResponseEntity<List<AdminCategoryResponse>> list(
            @RequestParam(required = false, defaultValue = "all") String status
    ) {
        return ResponseEntity.ok(adminOpsService.listCategories(status));
    }

    @PostMapping
    public ResponseEntity<AdminCategoryResponse> create(@RequestBody AdminCategoryRequest request) {
        return ResponseEntity.ok(adminOpsService.createCategory(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminCategoryResponse> update(
            @PathVariable String id,
            @RequestBody AdminCategoryRequest request
    ) {
        return ResponseEntity.ok(adminOpsService.updateCategory(id, request));
    }
}
