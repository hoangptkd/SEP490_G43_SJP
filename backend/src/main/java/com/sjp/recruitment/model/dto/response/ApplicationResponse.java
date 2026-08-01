package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ApplicationResponse(
        String id,
        JobResponse job,
        CandidateProfileResponse candidate,
        CvResponse cv,
        CvVersionResponse cvVersion,
        String preferredLocation,
        String coverLetter,
        String status,
        LocalDateTime submittedAt,
        LocalDateTime updatedAt,
        List<ApplicationTimelineResponse> timeline,
        List<InterviewScheduleResponse> interviews,
        JobOfferResponse jobOffer
) {
}
