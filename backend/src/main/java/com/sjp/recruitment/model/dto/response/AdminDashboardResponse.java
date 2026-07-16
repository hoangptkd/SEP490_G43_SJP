package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;

public record AdminDashboardResponse(
        long totalUsers,
        long activeJobs,
        long pendingModeration,
        long applicationsToday,
        long pendingCompanies,
        long pendingJobs,
        long verifiedCompanies,
        long totalCompanies,
        long totalApplications,
        long totalEmployers,
        long totalCandidates,
        LocalDateTime updatedAt
) {
}
