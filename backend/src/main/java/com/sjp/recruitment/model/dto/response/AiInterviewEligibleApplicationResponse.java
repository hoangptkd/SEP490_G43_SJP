package com.sjp.recruitment.model.dto.response;

public record AiInterviewEligibleApplicationResponse(
        String id,
        String status,
        String submittedAt,
        JobResponse job
) {
}
