package com.sjp.recruitment.service;

import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.model.entity.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DtoMapper {

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getRoleEnum() == null ? user.getRole() : user.getRoleEnum().name(),
                user.getStatusEnum() == null ? user.getStatus() : user.getStatusEnum().name(),
                user.isEmailVerified()
        );
    }

    public CandidateProfileResponse toCandidateProfileResponse(CandidateProfile profile, boolean applyReady) {
        return new CandidateProfileResponse(
                String.valueOf(profile.getId()),
                String.valueOf(profile.getUser().getId()),
                profile.getFullName(),
                profile.getPhone(),
                profile.getLocation(),
                profile.getBio(),
                safeList(profile.getSkills()),
                safeObjectList(profile.getEducation()),
                safeObjectList(profile.getWorkExperience()),
                safeObjectList(profile.getProjects()),
                safeObjectList(profile.getCertifications()),
                applyReady
        );
    }

    public CvResponse toCvResponse(CandidateCv cv) {
        if (cv == null) {
            return null;
        }
        return new CvResponse(
                String.valueOf(cv.getId()),
                cv.getOriginalFileName(),
                cv.getContentType(),
                cv.getFileSize(),
                cv.isDefaultCv(),
                cv.isDeleted(),
                cv.getCreatedAt()
        );
    }

    public CvVersionResponse toCvVersionResponse(CvVersion version) {
        if (version == null) {
            return null;
        }
        return new CvVersionResponse(
                String.valueOf(version.getId()),
                version.getTitle(),
                version.getTemplateKey(),
                version.getSnapshot(),
                version.getUpdatedAt()
        );
    }

    public CompanyResponse toCompanyResponse(Company company) {
        return new CompanyResponse(String.valueOf(company.getId()), company.getName(), company.getWebsite(), company.getLocation());
    }

    public CompanyLocationResponse toCompanyLocationResponse(CompanyLocation location) {
        if (location == null) {
            return null;
        }
        return new CompanyLocationResponse(
                String.valueOf(location.getId()),
                location.getBranchName(),
                location.getAddress(),
                location.getCity(),
                location.getDistrict(),
                location.getCountry(),
                location.isHeadquarter()
        );
    }

    public JobResponse toJobResponse(Job job, boolean saved, boolean applied, Integer matchScore) {
        return new JobResponse(
                String.valueOf(job.getId()),
                job.getTitle(),
                job.getDescription(),
                safeList(job.getRequirements()),
                safeList(job.getSkills()),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getLocation(),
                job.getExperienceLevel(),
                job.getDeadline() == null ? null : job.getDeadline().atStartOfDay(),
                toFrontendJobStatus(job.getStatus()),
                toCompanyResponse(job.getCompany()),
                job.getCompanyLocation() == null ? null : String.valueOf(job.getCompanyLocation().getId()),
                toCompanyLocationResponse(job.getCompanyLocation()),
                saved,
                applied,
                matchScore
        );
    }

    public ApplicationTimelineResponse toTimelineResponse(ApplicationStatusHistory history) {
        return new ApplicationTimelineResponse(
                String.valueOf(history.getId()),
                toFrontendApplicationStatus(history.getFromStatus()),
                toFrontendApplicationStatus(history.getToStatus()),
                history.getPublicNote(),
                history.getCreatedAt()
        );
    }

    public ApplicationResponse toApplicationResponse(
            Application application,
            JobResponse job,
            List<ApplicationTimelineResponse> timeline) {
        return new ApplicationResponse(
                String.valueOf(application.getId()),
                job,
                toCvResponse(application.getCv()),
                toCvVersionResponse(application.getCvVersion()),
                toFrontendApplicationStatus(application.getStatus()),
                application.getSubmittedAt(),
                application.getUpdatedAt(),
                timeline
        );
    }

    public NotificationResponse toNotificationResponse(Notification notification) {
        return new NotificationResponse(
                String.valueOf(notification.getId()),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getRelatedEntityType(),
                notification.getRelatedEntityId() == null ? null : String.valueOf(notification.getRelatedEntityId()),
                notification.getCreatedAt()
        );
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private List<Object> safeObjectList(List<Object> values) {
        return values == null ? List.of() : values;
    }

    private String toFrontendJobStatus(String status) {
        if (status == null) {
            return "DRAFT";
        }
        return switch (status.toLowerCase()) {
            case "published" -> "ACTIVE";
            case "closed" -> "CLOSED";
            case "expired" -> "EXPIRED";
            default -> "DRAFT";
        };
    }

    private String toFrontendApplicationStatus(String status) {
        if (status == null) {
            return null;
        }
        return switch (status.toLowerCase()) {
            case "applied" -> "SUBMITTED";
            case "reviewed" -> "UNDER_REVIEW";
            case "shortlisted" -> "SHORTLISTED";
            case "interview_scheduled" -> "INTERVIEW_SCHEDULED";
            case "accepted" -> "ACCEPTED";
            case "rejected" -> "REJECTED";
            case "withdrawn" -> "WITHDRAWN";
            default -> status.toUpperCase();
        };
    }
}
