package com.sjp.recruitment.service;

import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.model.entity.*;
import com.sjp.recruitment.repository.ApplicationRepository;
import com.sjp.recruitment.repository.JobReviewHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Component
public class DtoMapper {

    @Autowired
    private JobReviewHistoryRepository jobReviewHistoryRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

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
        if (profile == null) {
            return null;
        }
        String userId = null;
        try {
            if (profile.getUser() != null && profile.getUser().getId() != null) {
                userId = String.valueOf(profile.getUser().getId());
            }
        } catch (Exception ignored) {}
        return new CandidateProfileResponse(
                String.valueOf(profile.getId()),
                userId,
                profile.getFullName(),
                profile.getPhone(),
                profile.getDateOfBirth(),
                profile.getDateOfBirth() == null ? null : Period.between(profile.getDateOfBirth(), LocalDate.now()).getYears(),
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
        if (company == null) return null;
        return new CompanyResponse(String.valueOf(company.getId()), company.getName(), company.getWebsite(), company.getLocation(), company.getLogoUrl());
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

    public CompanyIndustryResponse toCompanyIndustryResponse(CompanyIndustry companyIndustry) {
        if (companyIndustry == null || companyIndustry.getCategory() == null) {
            return null;
        }
        Category cat = companyIndustry.getCategory();
        return new CompanyIndustryResponse(
                companyIndustry.getId(),
                cat.getId(),
                cat.getName(),
                cat.getSlug(),
                companyIndustry.isPrimary()
        );
    }

    public CompanyDocumentResponse toCompanyDocumentResponse(CompanyDocument doc) {
        if (doc == null) {
            return null;
        }
        return new CompanyDocumentResponse(
                String.valueOf(doc.getId()),
                doc.getFileName(),
                doc.getFileUrl(),
                doc.getFileType(),
                doc.getStatus(),
                doc.getRejectReason(),
                doc.getUploadedAt(),
                doc.getReviewedAt()
        );
    }

    public JobResponse toJobResponse(Job job, boolean saved, boolean applied, Integer matchScore) {
        String rejectionReason = job.getRejectionReason();
        if (rejectionReason == null && "rejected".equalsIgnoreCase(job.getStatus()) && jobReviewHistoryRepository != null && job.getId() != null) {
            rejectionReason = jobReviewHistoryRepository.findFirstByJobIdAndActionOrderByReviewedAtDesc(job.getId(), "REJECTED")
                    .map(JobReviewHistory::getReason)
                    .orElse(null);
        }
        String frontendStatus = toFrontendJobStatus(job.getStatus());
        if (("PUBLISHED".equals(frontendStatus) || "ACTIVE".equals(frontendStatus))
                && job.getDeadline() != null && job.getDeadline().isBefore(java.time.LocalDate.now())) {
            frontendStatus = "EXPIRED";
        }
        long appsCount = (applicationRepository != null && job.getId() != null) ? applicationRepository.countByJobId(job.getId()) : 0L;
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
                frontendStatus,
                toCompanyResponse(job.getCompany()),
                job.getCompanyLocation() == null ? null : String.valueOf(job.getCompanyLocation().getId()),
                toCompanyLocationResponse(job.getCompanyLocation()),
                saved,
                applied,
                matchScore,
                job.getBenefits(),
                job.getVacancies(),
                job.getWorkingTime(),
                job.getSalaryType(),
                job.getJobType(),
                job.getWorkMode(),
                job.getViewsCount() != null ? job.getViewsCount() : 0,
                rejectionReason,
                appsCount
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
            List<ApplicationTimelineResponse> timeline,
            List<InterviewScheduleResponse> interviews,
            JobOfferResponse jobOffer) {
        CandidateCv submittedCv = application.getCv();
        CvVersion submittedVersion = application.getCvVersion();
        boolean builderResume = submittedVersion != null && "builder".equalsIgnoreCase(submittedVersion.getSourceType());
        return new ApplicationResponse(
                String.valueOf(application.getId()),
                job,
                application.getCandidate() != null ? toCandidateProfileResponse(application.getCandidate(), true) : null,
                builderResume ? null : toCvResponse(submittedCv),
                builderResume ? toCvVersionResponse(submittedVersion) : null,
                application.getPreferredLocation(),
                application.getCoverLetter(),
                toFrontendApplicationStatus(application.getStatus()),
                application.getSubmittedAt(),
                application.getUpdatedAt(),
                timeline,
                interviews,
                jobOffer
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
        try {
            return values == null ? List.of() : new ArrayList<>(values);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Object> safeObjectList(List<Object> values) {
        try {
            return values == null ? List.of() : new ArrayList<>(values);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toFrontendJobStatus(String status) {
        if (status == null) {
            return "DRAFT";
        }
        return switch (status.toLowerCase()) {
            case "published", "active" -> "PUBLISHED";
            case "pending_review" -> "PENDING_REVIEW";
            case "awaiting_company" -> "AWAITING_COMPANY";
            case "rejected" -> "REJECTED";
            case "closed" -> "CLOSED";
            case "removed" -> "REMOVED";
            case "expired" -> "EXPIRED";
            default -> status.toUpperCase();
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

    public InterviewScheduleResponse toInterviewScheduleResponse(InterviewSchedule schedule) {
        if (schedule == null) return null;
        return new InterviewScheduleResponse(
                schedule.getId(),
                schedule.getApplication() != null ? schedule.getApplication().getId() : null,
                schedule.getRoundNumber(),
                schedule.getScheduledAt(),
                schedule.getMeetingLink(),
                schedule.getLocation(),
                schedule.getStatus(),
                schedule.getNote(),
                schedule.getCandidateResponse(),
                schedule.getCandidateResponseAt(),
                schedule.getCandidateRescheduleNote(),
                schedule.getEmployerRescheduleResponse(),
                schedule.getEmployerRescheduleNote(),
                schedule.getEmployerRescheduleAt(),
                schedule.getInterviewResult(),
                schedule.getInterviewResultNote(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt()
        );
    }

    public JobOfferResponse toJobOfferResponse(JobOffer offer) {
        if (offer == null) return null;
        return new JobOfferResponse(
                offer.getId(),
                offer.getApplication() != null ? offer.getApplication().getId() : null,
                offer.getPositionTitle(),
                offer.getSalary(),
                offer.getSalaryCurrency(),
                offer.getSalaryType(),
                offer.getStartDate(),
                offer.getBenefits(),
                offer.getWorkingLocation(),
                offer.getOfferLetterUrl(),
                offer.getStatus(),
                offer.getSentAt(),
                offer.getRespondedAt(),
                offer.getExpiresAt(),
                offer.getCandidateNote(),
                offer.getEmployerNote(),
                offer.getCreatedAt(),
                offer.getUpdatedAt()
        );
    }
}
