package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.JobRequest;
import com.sjp.recruitment.model.dto.response.JobPageResponse;
import com.sjp.recruitment.model.dto.response.JobResponse;
import com.sjp.recruitment.model.dto.response.RecommendationResponse;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(jobService.search(search, location, minSalary, maxSalary, experienceLevel, skills, sort, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJobById(@PathVariable String id) {
        return ResponseEntity.ok(jobService.findJobResponseById(id));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<RecommendationResponse>> recommendations() {
        return ResponseEntity.ok(jobService.recommendations());
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody JobRequest request) {
        return ResponseEntity.ok(jobService.createJobResponse(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobResponse> updateJob(@PathVariable String id, @Valid @RequestBody JobRequest request) {
        return ResponseEntity.ok(jobService.updateJobResponse(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable String id) {
        jobService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/employer/{employerId}")
    public ResponseEntity<Page<Job>> getJobsByEmployer(
            @PathVariable String employerId,
            Pageable pageable) {
        Page<Job> jobs = jobService.findByEmployerId(employerId, pageable);
        return ResponseEntity.ok(jobs);
    }
}
