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
        List<DailyApplicationTrend> applicationTrend,
        List<UpcomingInterview> upcomingInterviews,
        List<PendingTask> pendingTasks,
        ActionSummary actionSummary,
        PipelineStats pipelineStats,
        List<ActiveJobSummary> activeJobsList,
        List<ActivityLog> recentActivities
) {
    public record DailyApplicationTrend(
            String date,
            long count
    ) {}

    public record UpcomingInterview(
            java.util.UUID id,
            String candidateName,
            String jobTitle,
            java.time.LocalDateTime scheduledAt,
            String type,
            String status,
            String meetingLink
    ) {}

    public record PendingTask(
            java.util.UUID id,
            String title,
            String description,
            String taskType,
            String actionUrl,
            java.time.LocalDateTime createdAt
    ) {}

    public record ActionSummary(
            long pendingApplicationsCount,
            long todayInterviewsCount,
            long expiringJobsCount,
            long unreadMessagesCount
    ) {}

    public record PipelineStats(
            long appliedCount,
            long reviewedCount,
            long interviewCount,
            long offerCount,
            long hiredCount,
            long newlyAppliedCount,
            long shortlistedCount,
            long interviewScheduledCount
    ) {}

    public record ActiveJobSummary(
            java.util.UUID id,
            String title,
            String location,
            String type,
            String status,
            long viewCount,
            long applicationCount,
            long daysLeft
    ) {}

    public record ActivityLog(
            java.util.UUID id,
            String title,
            String description,
            java.time.LocalDateTime createdAt,
            String type
    ) {}
}
