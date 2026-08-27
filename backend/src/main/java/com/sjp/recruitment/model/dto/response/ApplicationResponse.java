package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ApplicationResponse(
        String id,
        JobResponse job,
        CandidateProfileResponse candidate,
        CvResponse cv,
        CvVersionResponse cvVersion,
        SubmittedResumeResponse submittedResume,
        String preferredLocation,
        String coverLetter,
        String status,
        LocalDateTime submittedAt,
        LocalDateTime updatedAt,
        List<ApplicationTimelineResponse> timeline,
        List<InterviewScheduleResponse> interviews,
        JobOfferResponse jobOffer,
        Integer aiMatchScore,
        String aiMatchAnalysis,
        com.fasterxml.jackson.databind.JsonNode missingRequirements,
        com.fasterxml.jackson.databind.JsonNode scoreBreakdown,
        Boolean needRerank
) {
}
