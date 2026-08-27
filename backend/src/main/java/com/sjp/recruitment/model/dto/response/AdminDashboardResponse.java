package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        BigDecimal revenueToday,
        BigDecimal revenueMonth,
        long paidCountMonth,
        long activeSubscriptions,
        long interviewsToday,
        long interviewsWeek,
        long interviewsCompletedWeek,
        long pendingJobReports,
        List<AdminTrendPointResponse> applicationsLast7Days,
        List<AdminTrendPointResponse> revenueLast7Days,
        LocalDateTime updatedAt
) {
}
