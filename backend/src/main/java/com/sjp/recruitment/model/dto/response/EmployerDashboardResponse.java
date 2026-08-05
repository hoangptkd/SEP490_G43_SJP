package com.sjp.recruitment.model.dto.response;

import java.util.List;
import java.util.Map;

public record EmployerDashboardResponse(
        long totalJobs,
        Integer jobGrowthPercentage,
        long activeJobs,
        long totalApplications,
        Integer applicationGrowthPercentage,
        long pendingApplications,
        Map<String, Long> applicationsByStatus,
        List<ApplicationResponse> recentApplications,
        List<DailyApplicationTrend> applicationTrend
) {
    public record DailyApplicationTrend(
            String date,
            long count
    ) {}
}
