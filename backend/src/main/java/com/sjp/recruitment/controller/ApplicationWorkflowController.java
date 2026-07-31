package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.InterviewCandidateResponseRequest;
import com.sjp.recruitment.model.dto.request.InterviewResultRequest;
import com.sjp.recruitment.model.dto.request.InterviewScheduleRequest;
import com.sjp.recruitment.model.dto.request.JobOfferRequest;
import com.sjp.recruitment.model.dto.response.InterviewScheduleResponse;
import com.sjp.recruitment.model.dto.response.JobOfferResponse;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.Employer;
import com.sjp.recruitment.service.ApplicationWorkflowService;
import com.sjp.recruitment.service.CandidateService;
import com.sjp.recruitment.service.EmployerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ApplicationWorkflowController {

    private final ApplicationWorkflowService workflowService;
    private final EmployerService employerService;
    private final CandidateService candidateService;

    @PostMapping("/applications/{id}/reject")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<Void> rejectApplication(
            @PathVariable UUID id,
            @RequestParam(required = false) String note) {
        Employer employer = employerService.getCurrentEmployerOrRegisterPlaceholder();
        workflowService.rejectApplication(id, employer.getId(), note);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/applications/{id}/interviews")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<InterviewScheduleResponse> scheduleInterview(
            @PathVariable UUID id,
            @Valid @RequestBody InterviewScheduleRequest request) {
        Employer employer = employerService.getCurrentEmployerOrRegisterPlaceholder();
        InterviewScheduleResponse response = workflowService.scheduleInterview(id, employer.getId(), request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/interviews/{id}/result")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<InterviewScheduleResponse> updateInterviewResult(
            @PathVariable UUID id,
            @Valid @RequestBody InterviewResultRequest request) {
        Employer employer = employerService.getCurrentEmployerOrRegisterPlaceholder();
        InterviewScheduleResponse response = workflowService.employerUpdateInterviewResult(id, employer.getId(), request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/interviews/{id}/employer-reschedule-response")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<InterviewScheduleResponse> employerRespondToReschedule(
            @PathVariable UUID id,
            @Valid @RequestBody com.sjp.recruitment.model.dto.request.EmployerRescheduleResponseRequest request) {
        Employer employer = employerService.getCurrentEmployerOrRegisterPlaceholder();
        InterviewScheduleResponse response = workflowService.employerRespondToReschedule(id, employer.getId(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/applications/{id}/offers")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<JobOfferResponse> createJobOffer(
            @PathVariable UUID id,
            @Valid @RequestBody JobOfferRequest request) {
        Employer employer = employerService.getCurrentEmployerOrRegisterPlaceholder();
        JobOfferResponse response = workflowService.createJobOffer(id, employer.getId(), request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/interviews/{id}/candidate-response")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<InterviewScheduleResponse> candidateRespondToInterview(
            @PathVariable UUID id,
            @Valid @RequestBody InterviewCandidateResponseRequest request) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        InterviewScheduleResponse response = workflowService.candidateRespondToInterview(id, candidate.getId(), request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/offers/{id}/response")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<JobOfferResponse> candidateRespondToOffer(
            @PathVariable UUID id,
            @RequestParam boolean accepted,
            @RequestParam(required = false) String note) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        JobOfferResponse response = workflowService.candidateRespondToOffer(id, candidate.getId(), accepted, note);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/offers/{id}/employer-response")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<JobOfferResponse> employerRespondToOfferRejection(
            @PathVariable UUID id,
            @RequestParam boolean isUpdating,
            @RequestBody(required = false) JobOfferRequest updateRequest) {
        Employer employer = employerService.getCurrentEmployerOrRegisterPlaceholder();
        JobOfferResponse response = workflowService.employerRespondToOfferRejection(id, employer.getId(), isUpdating, updateRequest);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/offers/{id}/candidate-final-response")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<JobOfferResponse> candidateFinalRespondToOffer(
            @PathVariable UUID id,
            @RequestParam boolean accepted) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        JobOfferResponse response = workflowService.candidateFinalRespondToOffer(id, candidate.getId(), accepted);
        return ResponseEntity.ok(response);
    }
}
