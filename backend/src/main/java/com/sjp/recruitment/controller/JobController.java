package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.response.JobPageResponse;
import com.sjp.recruitment.model.dto.response.JobResponse;
import com.sjp.recruitment.model.dto.response.RecommendationResponse;
import com.sjp.recruitment.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @GetMapping
    public ResponseEntity<JobPageResponse> getAllJobs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) BigDecimal minSalary,
            @RequestParam(required = false) BigDecimal maxSalary,
            @RequestParam(required = false) String experienceLevel,
            @RequestParam(required = false) String skills,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(jobService.search(search, location, minSalary, maxSalary, experienceLevel, skills, category, sort, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJobById(@PathVariable String id) {
        return ResponseEntity.ok(jobService.findJobResponseById(id));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<RecommendationResponse>> recommendations() {
        return ResponseEntity.ok(jobService.recommendations());
    }

}
