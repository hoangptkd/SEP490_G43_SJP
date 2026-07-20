package com.sjp.recruitment.model.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record AdminStatisticsResponse(
        long totalUsers,
        long totalCompanies,
        long totalJobs,
        long totalApplications,
        long totalViews,
        List<AdminStatItemResponse> usersByRole,
        List<AdminStatItemResponse> usersByStatus,
        List<AdminStatItemResponse> companiesByVerification,
        List<AdminStatItemResponse> jobsByStatus,
        List<AdminStatItemResponse> applicationsByStatus,
        List<AdminTrendPointResponse> usersTrend,
        List<AdminTrendPointResponse> candidateUsersTrend,
        List<AdminTrendPointResponse> employerUsersTrend,
        List<AdminTrendPointResponse> applicationsLast7Days,
        List<AdminTrendPointResponse> jobsLast7Days,
        LocalDateTime updatedAt
) {
}
