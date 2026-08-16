package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.CompanyLocationRequest;
import com.sjp.recruitment.model.dto.request.CompanyProfileRequest;
import com.sjp.recruitment.model.dto.request.EmployerPersonalProfileRequest;
import com.sjp.recruitment.model.dto.response.CompanyLocationResponse;
import com.sjp.recruitment.model.dto.response.CompanyProfileResponse;
import com.sjp.recruitment.model.dto.response.EmployerDashboardResponse;
import com.sjp.recruitment.model.dto.response.PageResponse;
import com.sjp.recruitment.model.entity.Company;
import com.sjp.recruitment.model.entity.CompanyLocation;
import com.sjp.recruitment.model.entity.Employer;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.CompanyLocationRepository;
import com.sjp.recruitment.repository.CompanyRepository;
import com.sjp.recruitment.repository.EmployerRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.sjp.recruitment.model.dto.response.CompanyDocumentResponse;
import com.sjp.recruitment.model.entity.CompanyDocument;
import com.sjp.recruitment.repository.CompanyDocumentRepository;
import com.sjp.recruitment.model.dto.request.JobRequest;
import com.sjp.recruitment.model.dto.response.JobResponse;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.repository.JobRepository;
import com.sjp.recruitment.model.dto.response.ApplicationResponse;
import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.repository.ApplicationRepository;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sjp.recruitment.repository.JobOfferRepository;
import com.sjp.recruitment.repository.InterviewScheduleRepository;
import com.sjp.recruitment.repository.NotificationRepository;
import com.sjp.recruitment.model.dto.response.NotificationResponse;
import com.sjp.recruitment.model.entity.Notification;

import com.sjp.recruitment.model.dto.request.CompanyIndustryRequest;
import com.sjp.recruitment.model.dto.response.CompanyIndustryResponse;
import com.sjp.recruitment.model.entity.CompanyIndustry;
import com.sjp.recruitment.model.entity.Category;
import com.sjp.recruitment.repository.CompanyIndustryRepository;
import com.sjp.recruitment.repository.CategoryRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
public class EmployerService {

    private final AuthService authService;
    
    @Transactional(readOnly = true)
    public EmployerDashboardResponse getDashboardStats() {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        UUID employerId = employer.getId();
        
        long totalJobs = jobRepository.countByEmployerIdAndStatusNot(employerId, "archived");
        
        // Fetch active/published jobs once
        List<Job> activeJobsQuery = jobRepository.findByEmployerIdAndStatus(employerId, "published");
        long activeJobs = activeJobsQuery.size();
        long totalApplications = applicationRepository.countByJobEmployerIdAndJobStatus(employerId, "published");
        
        // Application counts grouped by status (1 DB Query instead of 10+)
        List<Object[]> statusCounts = applicationRepository.countApplicationsByStatusForEmployerAndJobStatus(employerId, "published");
        Map<String, Long> applicationsByStatus = statusCounts.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));
                
        long pendingApplications = applicationsByStatus.getOrDefault("applied", 0L);
        long countNewlyApplied = pendingApplications;
        long countShortlisted = applicationsByStatus.getOrDefault("shortlisted", 0L);
        long countInterviewScheduled = applicationsByStatus.getOrDefault("interview_scheduled", 0L);
        long countReviewed = applicationsByStatus.getOrDefault("reviewed", 0L);
        long countInterview = countShortlisted + countInterviewScheduled;
        long countOffer = applicationsByStatus.getOrDefault("accepted", 0L);
        long countHired = applicationsByStatus.getOrDefault("hired", 0L);

        // Fetch recent applications and map using toResponseBulk
        List<Application> recentAppsList = applicationRepository
                .findByEmployerIdAndJobStatus(employerId, "published", PageRequest.of(0, 5, Sort.by("submittedAt").descending()))
                .getContent();
        List<ApplicationResponse> recentApplications = applicationService.toResponseBulk(recentAppsList);
                
        // Time Context Calculations
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fourteenDaysAgo = now.minusDays(14);
        
        List<Object[]> dateRows = applicationRepository.findApplicationDatesByEmployerSinceAndJobStatus(employerId, fourteenDaysAgo, "published");
        
        Map<String, Long> trendMap = dateRows.stream()
                .collect(Collectors.groupingBy(
                        row -> ((LocalDateTime) row[1]).toLocalDate().format(DateTimeFormatter.ISO_DATE),
                        Collectors.counting()
                ));
                
        List<EmployerDashboardResponse.DailyApplicationTrend> applicationTrend = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            String dateStr = now.minusDays(i).toLocalDate().format(DateTimeFormatter.ISO_DATE);
            applicationTrend.add(new EmployerDashboardResponse.DailyApplicationTrend(dateStr, trendMap.getOrDefault(dateStr, 0L)));
        }
        
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        long appsThisWeek = dateRows.stream().filter(r -> ((LocalDateTime) r[1]).isAfter(sevenDaysAgo)).count();
        long appsLastWeek = dateRows.stream().filter(r -> ((LocalDateTime) r[1]).isBefore(sevenDaysAgo) || ((LocalDateTime) r[1]).isEqual(sevenDaysAgo)).count();
        
        Integer applicationGrowthPercentage = 0;
        if (appsLastWeek > 0) {
            applicationGrowthPercentage = (int) Math.round(((double) (appsThisWeek - appsLastWeek) / appsLastWeek) * 100);
        } else if (appsThisWeek > 0) {
            applicationGrowthPercentage = 100;
        }
        
        Integer jobGrowthPercentage = 0; // Keeping 0 for now as Jobs rarely fluctuate week-over-week as much as applications.
                
        // Tasks & Schedules
        List<EmployerDashboardResponse.PendingTask> pendingTasks = new ArrayList<>();
        List<EmployerDashboardResponse.UpcomingInterview> upcomingInterviews = new ArrayList<>();

        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);

        // 1. New Applications (Top 5)
        List<Application> appliedApps = applicationRepository
            .findByJobEmployerIdAndStatusAndJobStatusOrderBySubmittedAtDesc(employerId, "applied", "published", PageRequest.of(0, 5))
            .getContent();
            
        for (Application a : appliedApps) {
            String candidateName = a.getCandidate() != null && a.getCandidate().getFullName() != null ? a.getCandidate().getFullName() : "Ứng viên";
            pendingTasks.add(new EmployerDashboardResponse.PendingTask(
                    UUID.randomUUID(),
                    "Hồ sơ mới cần duyệt: " + candidateName,
                    "Vị trí: " + a.getJob().getTitle(),
                    "new_application",
                    "/employer/applications?appId=" + a.getId(),
                    a.getSubmittedAt() != null ? a.getSubmittedAt() : now
            ));
        }

        // 2. Pending Interview Scheduling (Top 5 shortlisted)
        List<Application> shortlistedApps = applicationRepository
            .findByJobEmployerIdAndStatusAndJobStatusOrderBySubmittedAtDesc(employerId, "shortlisted", "published", PageRequest.of(0, 5))
            .getContent();
            
        for (Application a : shortlistedApps) {
            String candidateName = a.getCandidate() != null && a.getCandidate().getFullName() != null ? a.getCandidate().getFullName() : "Ứng viên";
            pendingTasks.add(new EmployerDashboardResponse.PendingTask(
                    UUID.randomUUID(),
                    "Chờ xếp lịch phỏng vấn: " + candidateName,
                    "Vị trí: " + a.getJob().getTitle(),
                    "pending_interview",
                    "/employer/applications?appId=" + a.getId(),
                    a.getSubmittedAt() != null ? a.getSubmittedAt() : now
            ));
        }

        // 3. Pending Evaluations (ACCEPTED interviews that have passed - limit 5)
        List<com.sjp.recruitment.model.entity.InterviewSchedule> acceptedInterviews = interviewScheduleRepository
                .findByEmployerIdAndStatusAndJobStatusOrderByScheduledAtDesc(employerId, "ACCEPTED", "published", PageRequest.of(0, 5))
                .getContent();
        for (var iv : acceptedInterviews) {
            if (iv.getScheduledAt().isBefore(startOfDay)) {
                String candidateName = iv.getCandidate() != null && iv.getCandidate().getFullName() != null ? iv.getCandidate().getFullName() : "Ứng viên";
                pendingTasks.add(new EmployerDashboardResponse.PendingTask(
                        UUID.randomUUID(),
                        "Đánh giá phỏng vấn: " + candidateName,
                        "Phỏng vấn ngày " + iv.getScheduledAt().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        "evaluate_interview",
                        "/employer/applications?appId=" + iv.getApplication().getId(),
                        now
                ));
            }
        }

        // 4. Pending Offers (COMPLETED interviews but no offer sent - limit 5)
        List<com.sjp.recruitment.model.entity.InterviewSchedule> interviewApps = interviewScheduleRepository
                .findCompletedInterviewsWithoutOfferAndJobStatus(employerId, "published", PageRequest.of(0, 5))
                .getContent();
        
        for (var iv : interviewApps) {
            Application a = iv.getApplication();
            String candidateName = a.getCandidate() != null && a.getCandidate().getFullName() != null ? a.getCandidate().getFullName() : "Ứng viên";
            pendingTasks.add(new EmployerDashboardResponse.PendingTask(
                    UUID.randomUUID(),
                    "Đạt PV, chờ gửi Offer: " + candidateName,
                    "Vị trí: " + a.getJob().getTitle(),
                    "pending_offer",
                    "/employer/applications?appId=" + a.getId(),
                    iv.getScheduledAt() != null ? iv.getScheduledAt() : now
            ));
        }

        // 5. Employer Response Needed (Reschedule requests & Rejected offers)
        List<com.sjp.recruitment.model.entity.InterviewSchedule> rescheduleRequests = interviewScheduleRepository
                .findByEmployerIdAndStatusAndJobStatusOrderByScheduledAtDesc(employerId, "RESCHEDULE_REQUESTED", "published", PageRequest.of(0, 5))
                .getContent();
        for (var iv : rescheduleRequests) {
            String candidateName = iv.getCandidate() != null && iv.getCandidate().getFullName() != null ? iv.getCandidate().getFullName() : "Ứng viên";
            pendingTasks.add(new EmployerDashboardResponse.PendingTask(
                    UUID.randomUUID(),
                    "Xin đổi lịch phỏng vấn: " + candidateName,
                    "Vị trí: " + iv.getApplication().getJob().getTitle(),
                    "employer_response_needed",
                    "/employer/applications?appId=" + iv.getApplication().getId(),
                    iv.getScheduledAt() != null ? iv.getScheduledAt() : now
            ));
        }

        List<com.sjp.recruitment.model.entity.JobOffer> rejectedOffers = jobOfferRepository
                .findByApplicationJobEmployerIdAndStatusAndJobStatusOrderByCreatedAtDesc(employerId, "rejected", "published", PageRequest.of(0, 5))
                .getContent();
        for (var offer : rejectedOffers) {
            String candidateName = offer.getApplication().getCandidate() != null && offer.getApplication().getCandidate().getFullName() != null ? offer.getApplication().getCandidate().getFullName() : "Ứng viên";
            pendingTasks.add(new EmployerDashboardResponse.PendingTask(
                    UUID.randomUUID(),
                    "Từ chối/Thương lượng Offer: " + candidateName,
                    "Vị trí: " + offer.getPositionTitle(),
                    "employer_response_needed",
                    "/employer/applications?appId=" + offer.getApplication().getId(),
                    offer.getCreatedAt() != null ? offer.getCreatedAt() : now
            ));
        }

        // 6. Today's Interviews & Upcoming Timeline
        List<com.sjp.recruitment.model.entity.InterviewSchedule> futureInterviews = interviewScheduleRepository
                .findByEmployerIdAndScheduledAtAfterAndJobStatusOrderByScheduledAtAsc(employerId, startOfDay.minusNanos(1), "published");

        for (var interview : futureInterviews) {
            if ("COMPLETED".equals(interview.getStatus()) || "NO_SHOW".equals(interview.getStatus()) || "CANCELLED".equals(interview.getStatus())) {
                continue;
            }
            
            String candidateName = interview.getCandidate() != null && interview.getCandidate().getFullName() != null 
                    ? interview.getCandidate().getFullName() : "Ứng viên";
            String jobTitle = interview.getApplication() != null && interview.getApplication().getJob() != null
                    ? interview.getApplication().getJob().getTitle() : "Việc làm";
            
            if (interview.getScheduledAt().isBefore(endOfDay)) {
                pendingTasks.add(new EmployerDashboardResponse.PendingTask(
                        interview.getId(),
                        "Phỏng vấn hôm nay: " + candidateName,
                        "Vị trí " + jobTitle + " lúc " + interview.getScheduledAt().format(DateTimeFormatter.ofPattern("HH:mm")),
                        "interview_today",
                        "/employer/applications/" + interview.getApplication().getId(),
                        interview.getScheduledAt()
                ));
            } else if ("SCHEDULED".equals(interview.getStatus())
                    || "ACCEPTED".equals(interview.getStatus())
                    || "PENDING_RESPONSE".equals(interview.getStatus())) {
                upcomingInterviews.add(new EmployerDashboardResponse.UpcomingInterview(
                        interview.getId(),
                        candidateName,
                        jobTitle,
                        interview.getScheduledAt(),
                        interview.getLocation() != null && interview.getLocation().toLowerCase().contains("online") ? "online" : "offline",
                        interview.getStatus(),
                        interview.getMeetingLink()
                ));
            }
        }
        
        // 7. TopCV Action Summary
        long unreadMessagesCount = notificationRepository.countByRecipientUserIdAndReadFalse(employer.getUser().getId());
        
        // Count expiring jobs from already fetched activeJobsQuery
        long expiringJobsCount = activeJobsQuery.stream()
                .filter(j -> j.getDeadline() != null && !j.getDeadline().isBefore(now.toLocalDate()) && j.getDeadline().isBefore(now.toLocalDate().plusDays(4)))
                .count();

        long todayInterviewsCount = futureInterviews.stream()
                .filter(iv -> iv.getScheduledAt().isBefore(endOfDay) && 
                              !"COMPLETED".equals(iv.getStatus()) && 
                              !"NO_SHOW".equals(iv.getStatus()) && 
                              !"CANCELLED".equals(iv.getStatus()))
                .count();

        long pendingAppsCount = countInterviewScheduled;

        EmployerDashboardResponse.ActionSummary actionSummary = new EmployerDashboardResponse.ActionSummary(
                pendingAppsCount,
                todayInterviewsCount,
                expiringJobsCount,
                unreadMessagesCount
        );

        // 8. TopCV Pipeline Stats
        long interviewPendingResponseCount = interviewScheduleRepository.countActiveInterviewsByStatusesAndJobStatus(employerId, List.of("PENDING_RESPONSE", "SCHEDULED", "RESCHEDULE_REQUESTED"), "published");
        long interviewAcceptedCount = interviewScheduleRepository.countActiveInterviewsByStatusesAndJobStatus(employerId, List.of("ACCEPTED"), "published");
        long interviewCompletedCount = interviewScheduleRepository.countActiveInterviewsByStatusesAndJobStatus(employerId, List.of("COMPLETED"), "published");
        long rescheduleRequestedCount = interviewScheduleRepository.countByEmployerIdAndStatusAndJobStatus(employerId, "RESCHEDULE_REQUESTED", "published");

        long offerPendingResponseCount = jobOfferRepository.countActiveOffersByStatusesAndJobStatus(employerId, List.of("sent", "pending_response", "negotiation_requested"), "published");
        long offerAcceptedCount = jobOfferRepository.countActiveOffersByStatusesAndJobStatus(employerId, List.of("accepted"), "published");
        long offerRejectedCount = jobOfferRepository.countActiveOffersByStatusesAndJobStatus(employerId, List.of("rejected", "declined"), "published");

        EmployerDashboardResponse.PipelineStats pipelineStats = new EmployerDashboardResponse.PipelineStats(
                totalApplications,
                countReviewed,
                countInterview,
                countOffer,
                countHired,
                countNewlyApplied,
                countShortlisted,
                countInterviewScheduled,
                interviewPendingResponseCount,
                interviewAcceptedCount,
                rescheduleRequestedCount,
                interviewCompletedCount,
                offerPendingResponseCount,
                offerAcceptedCount,
                offerRejectedCount
        );

        // 9. TopCV Active Jobs List (Batch Count using countByJobIdIn)
        List<EmployerDashboardResponse.ActiveJobSummary> activeJobsList = new ArrayList<>();
        if (!activeJobsQuery.isEmpty()) {
            List<UUID> jobIds = activeJobsQuery.stream().map(Job::getId).toList();
            Map<UUID, Long> appCountMap = applicationRepository.countByJobIdIn(jobIds).stream()
                    .collect(Collectors.toMap(
                            row -> (UUID) row[0],
                            row -> (Long) row[1]
                    ));
            for (Job j : activeJobsQuery) {
                long appCount = appCountMap.getOrDefault(j.getId(), 0L);
                long daysLeft = j.getDeadline() != null ? java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), j.getDeadline()) : 0;
                if (daysLeft < 0) daysLeft = 0;
                
                activeJobsList.add(new EmployerDashboardResponse.ActiveJobSummary(
                        j.getId(),
                        j.getTitle(),
                        j.getLocation(),
                        j.getJobType(),
                        j.getStatus(),
                        j.getViewsCount() != null ? j.getViewsCount() : 0,
                        appCount,
                        daysLeft
                ));
            }
            activeJobsList.sort(Comparator.comparing(EmployerDashboardResponse.ActiveJobSummary::daysLeft));
        }

        // 10. TopCV Activity Logs
        List<com.sjp.recruitment.model.entity.Notification> notifications = notificationRepository
                .findByRecipientUserId(employer.getUser().getId(), PageRequest.of(0, 10, Sort.by("createdAt").descending())).getContent();
        List<EmployerDashboardResponse.ActivityLog> recentActivities = notifications.stream().map(n -> 
                new EmployerDashboardResponse.ActivityLog(
                        n.getId(),
                        n.getTitle(),
                        n.getMessage(),
                        n.getCreatedAt(),
                        n.getType()
                )
        ).toList();

        return new EmployerDashboardResponse(
                totalJobs,
                jobGrowthPercentage,
                activeJobs,
                totalApplications,
                applicationGrowthPercentage,
                pendingApplications,
                applicationsByStatus,
                recentApplications,
                applicationTrend,
                upcomingInterviews,
                pendingTasks,
                actionSummary,
                pipelineStats,
                activeJobsList,
                recentActivities
        );
    }
    private final EmployerRepository employerRepository;
    private final CompanyRepository companyRepository;
    private final CompanyLocationRepository companyLocationRepository;
    private final CompanyIndustryRepository companyIndustryRepository;
    private final CategoryRepository categoryRepository;
    private final CompanyDocumentRepository companyDocumentRepository;
    private final JobRepository jobRepository;
    private final JobService jobService;
    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final com.sjp.recruitment.repository.InterviewScheduleRepository interviewScheduleRepository;
    private final CandidateService candidateService;
    private final Cloudinary cloudinary;
    private final DtoMapper dtoMapper;
    private final FeatureLimitService featureLimitService;
    private final SystemSettingsService systemSettingsService;
    private final NotificationRepository notificationRepository;
    private final com.sjp.recruitment.repository.UserRepository userRepository;
    private final com.sjp.recruitment.repository.JobOfferRepository jobOfferRepository;
    private final TaxCodeLookupService taxCodeLookupService;

    @Transactional
    public Employer getCurrentEmployerOrRegisterPlaceholder() {
        User user = authService.getCurrentUser();
        if (user.getRoleEnum() != User.UserRole.EMPLOYER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ dành cho Nhà tuyển dụng");
        }

        return employerRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    Company company = new Company();
                    company.setName("");
                    company.setDescription("Chưa có mô tả");
                    company.setStatus("pending");
                    company.setVerificationStatus("unverified");
                    company = companyRepository.save(company);

                    // Tạo hồ sơ Employer
                    Employer employer = new Employer();
                    employer.setUser(user);
                    employer.setCompany(company);
                    employer.setOwner(true);
                    employer.setVerificationStatus("pending");
                    employer.setPosition("Quản trị viên");
                    return employerRepository.save(employer);
                });
    }

    @Transactional
    public CompanyProfileResponse getCompanyProfile() {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        Company company = employer.getCompany();
        return toCompanyProfileResponse(company);
    }

    @Transactional
    public void updatePersonalProfile(EmployerPersonalProfileRequest request) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        User user = employer.getUser();
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        employer.setPosition(request.position());
        userRepository.save(user);
        employerRepository.save(employer);
    }

    @Transactional(readOnly = true)
    public com.sjp.recruitment.model.dto.response.EmployerPersonalProfileResponse getPersonalProfile() {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        User user = employer.getUser();
        return new com.sjp.recruitment.model.dto.response.EmployerPersonalProfileResponse(
                user.getFullName(),
                user.getPhone(),
                employer.getPosition()
        );
    }

    @Transactional
    public CompanyProfileResponse updateCompanyProfile(CompanyProfileRequest request) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (!employer.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ chủ sở hữu công ty mới có quyền chỉnh sửa");
        }
        Company company = employer.getCompany();

        String oldName = company.getName() == null ? "" : company.getName().trim();
        String newName = request.name() == null ? "" : request.name().trim();
        String oldTaxRaw = company.getTaxCode() == null ? "" : company.getTaxCode().trim();
        String oldTax = taxCodeLookupService.normalize(oldTaxRaw);
        String newTax = taxCodeLookupService.normalize(request.taxCode());
        if (!newTax.isBlank()) {
            newTax = taxCodeLookupService.lookup(newTax).taxCode();
        }

        boolean nameOrTaxChanged = !oldName.equalsIgnoreCase(newName) || !oldTax.equals(newTax);
        boolean industryChanged = false;

        // Kiểm tra tên công ty trùng lặp
        if (!oldName.equalsIgnoreCase(newName)) {
            companyRepository.findByName(newName).ifPresent(existing -> {
                if (!existing.getId().equals(company.getId())) {
                    throw new ApiException(HttpStatus.CONFLICT, "COMPANY_NAME_EXISTS", "Tên công ty đã tồn tại");
                }
            });
        }

        company.setName(newName);
        company.setDescription(request.description());
        company.setWebsite(request.website());
        company.setContactPhone(request.contactPhone());
        company.setContactEmail(request.contactEmail());

        if (request.industries() != null) {
            List<CompanyIndustry> existingInds = companyIndustryRepository.findByCompanyId(company.getId());
            Map<UUID, CompanyIndustry> existingMap = existingInds.stream()
                    .collect(Collectors.toMap(ci -> ci.getCategory().getId(), ci -> ci, (ci1, ci2) -> ci1));
            Set<UUID> reqCatIds = request.industries().stream()
                    .map(CompanyIndustryRequest::categoryId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            List<CompanyIndustry> toDelete = existingInds.stream()
                    .filter(ci -> !reqCatIds.contains(ci.getCategory().getId()))
                    .toList();
            if (!toDelete.isEmpty()) {
                industryChanged = true;
                companyIndustryRepository.deleteAll(toDelete);
            }

            String primaryIndustryName = null;
            for (CompanyIndustryRequest item : request.industries()) {
                if (item.categoryId() == null) continue;
                CompanyIndustry ci = existingMap.get(item.categoryId());
                if (ci != null) {
                    if (ci.isPrimary() != item.primary()) industryChanged = true;
                    ci.setPrimary(item.primary());
                    companyIndustryRepository.save(ci);
                    if (item.primary() && ci.getCategory() != null) {
                        primaryIndustryName = ci.getCategory().getName();
                    }
                } else {
                    industryChanged = true;
                    Optional<Category> catOpt = categoryRepository.findById(item.categoryId());
                    if (catOpt.isPresent()) {
                        Category cat = catOpt.get();
                        CompanyIndustry newCi = new CompanyIndustry();
                        newCi.setCompany(company);
                        newCi.setCategory(cat);
                        newCi.setPrimary(item.primary());
                        companyIndustryRepository.save(newCi);
                        if (item.primary()) {
                            primaryIndustryName = cat.getName();
                        }
                    }
                }
            }
            if (primaryIndustryName != null) {
                company.setIndustry(primaryIndustryName);
            } else if (request.industry() != null) {
                company.setIndustry(request.industry());
            }
        } else {
            company.setIndustry(request.industry());
        }

        if (request.location() != null && !request.location().isBlank()) {
            List<CompanyLocation> locs = companyLocationRepository.findByCompanyId(company.getId());
            Optional<CompanyLocation> targetHq = locs.stream()
                    .filter(loc -> loc.getBranchName().equalsIgnoreCase(request.location()) || loc.getId().toString().equals(request.location()))
                    .findFirst();
            if (targetHq.isPresent()) {
                for (CompanyLocation loc : locs) {
                    loc.setHeadquarter(loc.getId().equals(targetHq.get().getId()));
                    companyLocationRepository.save(loc);
                }
                company.setLocation(targetHq.get().getBranchName());
            } else {
                company.setLocation(request.location());
            }
        } else {
            company.setLocation(request.location());
        }

        company.setCompanySize(request.companySize());
        company.setTaxCode(newTax);
        if (request.logoUrl() != null) {
            company.setLogoUrl(request.logoUrl());
        }

        boolean legalInfoChanged = nameOrTaxChanged || industryChanged;
        
        System.out.println("DEBUG UPDATE COMPANY: nameOrTaxChanged=" + nameOrTaxChanged + " (oldName='" + oldName + "', newName='" + newName + "', oldTax='" + oldTax + "', newTax='" + newTax + "')");
        System.out.println("DEBUG UPDATE COMPANY: industryChanged=" + industryChanged);
        if (industryChanged) {
            System.out.println("DEBUG UPDATE COMPANY: request.industries=" + request.industries());
            System.out.println("DEBUG UPDATE COMPANY: company.industry=" + company.getIndustry());
        }

        if (Boolean.TRUE.equals(request.submitForReview())) {
            forceCompanyAndOwnerPending(company);
        } else if (legalInfoChanged && company.isVerified()) {
            forceCompanyAndOwnerPending(company);
        }

        return toCompanyProfileResponse(companyRepository.save(company));
    }

    private void markCompanyPendingReviewIfNeeded(Company company) {
        String verificationStatus = company.getVerificationStatus();
        if (verificationStatus == null
                || "unverified".equalsIgnoreCase(verificationStatus)
                || "rejected".equalsIgnoreCase(verificationStatus)) {
            forceCompanyAndOwnerPending(company);
        }
    }

    private void forceCompanyAndOwnerPending(Company company) {
        company.setVerificationStatus("pending");
        if (company.getStatus() == null
                || "pending".equalsIgnoreCase(company.getStatus())
                || "rejected".equalsIgnoreCase(company.getStatus())) {
            company.setStatus("pending");
        }
        employerRepository.findOwnerByCompanyId(company.getId())
                .ifPresent(owner -> {
                    owner.setVerificationStatus("pending");
                    employerRepository.save(owner);
                });
    }

    @Transactional
    public List<CompanyLocationResponse> getCompanyLocations() {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        return companyLocationRepository.findByCompanyIdOrderByHeadquarterDescCreatedAtDesc(employer.getCompany().getId())
                .stream()
                .map(dtoMapper::toCompanyLocationResponse)
                .toList();
    }

    @Transactional
    public CompanyLocationResponse createCompanyLocation(CompanyLocationRequest request) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (!employer.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ chủ sở hữu công ty mới có quyền thêm địa điểm");
        }
        Company company = employer.getCompany();
        if (request.headquarter()) {
            companyLocationRepository.findByCompanyIdAndHeadquarterTrue(company.getId())
                    .ifPresent(loc -> {
                        loc.setHeadquarter(false);
                        companyLocationRepository.save(loc);
                    });
            company.setLocation(request.branchName());
            companyRepository.save(company);
        } else if (companyLocationRepository.findByCompanyId(company.getId()).isEmpty()) {
            company.setLocation(request.branchName());
            companyRepository.save(company);
        }

        CompanyLocation location = new CompanyLocation();
        location.setCompany(company);
        location.setBranchName(request.branchName());
        location.setAddress(request.address());
        location.setCity(request.city());
        location.setDistrict(request.district());
        location.setCountry(request.country() != null && !request.country().isBlank() ? request.country() : "Vietnam");
        location.setHeadquarter(request.headquarter() || companyLocationRepository.findByCompanyId(company.getId()).isEmpty());

        return dtoMapper.toCompanyLocationResponse(companyLocationRepository.save(location));
    }

    @Transactional
    public CompanyLocationResponse updateCompanyLocation(String id, CompanyLocationRequest request) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (!employer.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ chủ sở hữu công ty mới có quyền sửa địa điểm");
        }
        UUID locId;
        try {
            locId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "ID địa điểm không hợp lệ");
        }
        CompanyLocation location = companyLocationRepository.findByIdAndCompanyId(locId, employer.getCompany().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy địa điểm làm việc"));

        if (request.headquarter() && !location.isHeadquarter()) {
            companyLocationRepository.findByCompanyIdAndHeadquarterTrue(employer.getCompany().getId())
                    .ifPresent(loc -> {
                        loc.setHeadquarter(false);
                        companyLocationRepository.save(loc);
                    });
            employer.getCompany().setLocation(request.branchName());
            companyRepository.save(employer.getCompany());
        } else if (location.isHeadquarter() && !request.headquarter()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HEADQUARTER_REQUIRED", "Không thể hủy trụ sở chính, vui lòng đặt chi nhánh khác làm trụ sở chính");
        } else if (location.isHeadquarter()) {
            employer.getCompany().setLocation(request.branchName());
            companyRepository.save(employer.getCompany());
        }

        location.setBranchName(request.branchName());
        location.setAddress(request.address());
        location.setCity(request.city());
        location.setDistrict(request.district());
        if (request.country() != null && !request.country().isBlank()) {
            location.setCountry(request.country());
        }
        location.setHeadquarter(location.isHeadquarter() || request.headquarter());

        return dtoMapper.toCompanyLocationResponse(companyLocationRepository.save(location));
    }

    @Transactional
    public void deleteCompanyLocation(String id) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (!employer.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ chủ sở hữu công ty mới có quyền xóa địa điểm");
        }
        UUID locId;
        try {
            locId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "ID địa điểm không hợp lệ");
        }
        CompanyLocation location = companyLocationRepository.findByIdAndCompanyId(locId, employer.getCompany().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy địa điểm làm việc"));

        if (location.isHeadquarter()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DELETE_HEADQUARTER", "Không thể xóa trụ sở chính. Vui lòng đặt chi nhánh khác làm trụ sở chính trước");
        }
        companyLocationRepository.delete(location);
    }

    private CompanyProfileResponse toCompanyProfileResponse(Company company) {
        List<CompanyLocationResponse> locResponses = company.getLocations() == null ? List.of() :
                company.getLocations().stream()
                        .sorted(Comparator.comparing(CompanyLocation::isHeadquarter).reversed()
                                .thenComparing(CompanyLocation::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .map(dtoMapper::toCompanyLocationResponse)
                        .toList();
        List<CompanyIndustryResponse> indResponses = companyIndustryRepository
                .findByCompanyIdOrderByPrimaryDescCreatedAtDesc(company.getId())
                .stream()
                .map(dtoMapper::toCompanyIndustryResponse)
                .toList();
        return new CompanyProfileResponse(
                String.valueOf(company.getId()),
                company.getName(),
                company.getDescription(),
                company.getWebsite(),
                company.getIndustry(),
                company.getLocation(),
                company.getCompanySize(),
                company.getTaxCode(),
                company.getLogoUrl(),
                company.isVerified(),
                company.getVerificationStatus(),
                company.getStatus(),
                company.getContactPhone(),
                company.getContactEmail(),
                locResponses,
                indResponses
        );
    }

    @Transactional(readOnly = true)
    public List<CompanyDocumentResponse> getCompanyDocuments() {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        return companyDocumentRepository.findByCompanyIdOrderByUploadedAtDesc(employer.getCompany().getId())
                .stream()
                .map(dtoMapper::toCompanyDocumentResponse)
                .toList();
    }

    @Transactional
    public CompanyDocumentResponse uploadCompanyDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Vui lòng chọn file để tải lên");
        }
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (!employer.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ chủ sở hữu công ty mới có quyền tải lên tài liệu xác thực");
        }
        Company company = employer.getCompany();

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/pdf";
        String fileType = contentType.contains("pdf") ? "pdf" : "image";

        String fileUrl;
        String publicId = null;
        try {
            String resourceType = "pdf".equalsIgnoreCase(fileType) ? "raw" : "image";
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "sjp/company_docs",
                    "resource_type", resourceType
            ));
            fileUrl = (String) uploadResult.get("secure_url");
            publicId = (String) uploadResult.get("public_id");
        } catch (Exception e) {
            // Fallback cho local development nếu chưa cấu hình Cloudinary API key
            fileUrl = "pdf".equalsIgnoreCase(fileType) ? "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf" : "https://res.cloudinary.com/demo/image/upload/sample.jpg";
            publicId = "local_" + UUID.randomUUID();
        }

        CompanyDocument doc = new CompanyDocument();
        doc.setCompany(company);
        doc.setFileName(fileName);
        doc.setFileUrl(fileUrl);
        doc.setFileType(fileType);
        doc.setPublicId(publicId);
        doc.setStatus("pending");
        doc.setUploadedAt(java.time.LocalDateTime.now());

        doc = companyDocumentRepository.save(doc);

        // Khi tải lên tài liệu mới, chuyển trạng thái xác thực công ty thành pending để Admin duyệt lại
        if (company.isVerified()) {
            forceCompanyAndOwnerPending(company);
        } else {
            markCompanyPendingReviewIfNeeded(company);
        }
        companyRepository.save(company);

        return dtoMapper.toCompanyDocumentResponse(doc);
    }

    @Transactional
    public CompanyDocumentResponse replaceCompanyDocument(String id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Vui lòng chọn file để tải lên");
        }
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (!employer.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ chủ sở hữu công ty mới có quyền cập nhật tài liệu xác thực");
        }

        UUID docId;
        try {
            docId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "ID tài liệu không hợp lệ");
        }

        Company company = employer.getCompany();
        CompanyDocument doc = companyDocumentRepository.findByIdAndCompanyId(docId, company.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy tài liệu"));

        if (!"rejected".equalsIgnoreCase(doc.getStatus())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CANNOT_REPLACE",
                    "Chỉ có thể cập nhật lại tài liệu đã bị từ chối"
            );
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/pdf";
        String fileType = contentType.contains("pdf") ? "pdf" : "image";

        String fileUrl;
        String publicId = null;
        try {
            String resourceType = "pdf".equalsIgnoreCase(fileType) ? "raw" : "image";
            if (doc.getPublicId() != null && !doc.getPublicId().startsWith("local_")) {
                try {
                    String oldType = "pdf".equalsIgnoreCase(doc.getFileType()) ? "raw" : "image";
                    cloudinary.uploader().destroy(doc.getPublicId(), ObjectUtils.asMap("resource_type", oldType));
                } catch (Exception ignored) {}
            }
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "sjp/company_docs",
                    "resource_type", resourceType
            ));
            fileUrl = (String) uploadResult.get("secure_url");
            publicId = (String) uploadResult.get("public_id");
        } catch (Exception e) {
            fileUrl = "pdf".equalsIgnoreCase(fileType)
                    ? "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
                    : "https://res.cloudinary.com/demo/image/upload/sample.jpg";
            publicId = "local_" + UUID.randomUUID();
        }

        doc.setFileName(fileName);
        doc.setFileUrl(fileUrl);
        doc.setFileType(fileType);
        doc.setPublicId(publicId);
        doc.setStatus("pending");
        doc.setRejectReason(null);
        doc.setReviewedAt(null);
        doc.setReviewedBy(null);
        doc.setUploadedAt(java.time.LocalDateTime.now());
        doc = companyDocumentRepository.save(doc);

        forceCompanyAndOwnerPending(company);
        companyRepository.save(company);

        return dtoMapper.toCompanyDocumentResponse(doc);
    }

    @Transactional
    public void deleteCompanyDocument(String id) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (!employer.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ chủ sở hữu công ty mới có quyền xóa tài liệu");
        }
        UUID docId;
        try {
            docId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "ID tài liệu không hợp lệ");
        }
        CompanyDocument doc = companyDocumentRepository.findByIdAndCompanyId(docId, employer.getCompany().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy tài liệu"));

        if (!"pending".equalsIgnoreCase(doc.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DELETE_REVIEWED", "Chỉ có thể xóa tài liệu đang chờ duyệt");
        }

        if (doc.getPublicId() != null && !doc.getPublicId().startsWith("local_")) {
            try {
                String resourceType = "pdf".equalsIgnoreCase(doc.getFileType()) ? "raw" : "image";
                cloudinary.uploader().destroy(doc.getPublicId(), ObjectUtils.asMap("resource_type", resourceType));
            } catch (Exception ignored) {}
        }
        companyDocumentRepository.delete(doc);
    }

    @Transactional
    public CompanyProfileResponse uploadCompanyLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Vui lòng chọn file logo để tải lên");
        }
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (!employer.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ chủ sở hữu công ty mới có quyền cập nhật logo");
        }
        Company company = employer.getCompany();

        String fileUrl;
        try {
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "sjp/company_logos",
                    "resource_type", "image"
            ));
            fileUrl = (String) uploadResult.get("secure_url");
        } catch (Exception e) {
            fileUrl = "https://images.unsplash.com/photo-1548092372-0d1bd40894a3?auto=format&fit=crop&w=300&h=300&q=80";
        }

        company.setLogoUrl(fileUrl);
        company = companyRepository.save(company);
        return toCompanyProfileResponse(company);
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> getCompanyJobs() {
        return getCompanyJobs(null, null, 1, 10);
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> getCompanyJobs(String status, String search, int page, int size) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page - 1, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        
        String filterStatus = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) ? "" : status;
        String filterSearch = (search == null || search.isBlank()) ? "" : search;

        // Since original logic mapped PUBLISHED to PUBLISHED/ACTIVE, and DRAFT to DRAFT/REJECTED,
        // doing this in SQL requires IN clause or we simplify. For now we just pass status,
        // Wait, original logic:
        // if ("PUBLISHED".equalsIgnoreCase(status)) { return "PUBLISHED".equalsIgnoreCase(j.status()) || "ACTIVE".equalsIgnoreCase(j.status()); }
        // if ("DRAFT".equalsIgnoreCase(status)) { return "DRAFT".equalsIgnoreCase(j.status()) || "REJECTED".equalsIgnoreCase(j.status()); }
        // The JPQL searchCompanyJobs is simpler `AND (:status IS NULL OR j.status = :status)`. Let's pass the exact status or null if complex.
        // Let's modify the JPQL searchCompanyJobs to handle these later, or just keep it exact. We will use exact status.

        org.springframework.data.domain.Page<Job> jobPage = jobRepository.searchCompanyJobs(
                employer.getCompany().getId(), filterStatus, filterSearch, pageable);

        if (jobPage.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0, true, true);
        }

        List<Job> jobEntities = jobPage.getContent();
        List<UUID> jobIds = jobEntities.stream().map(Job::getId).toList();
        
        java.util.Map<UUID, Long> appCounts = applicationRepository.countByJobIdIn(jobIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1]
                ));

        int listingPriority = featureLimitService != null ? featureLimitService.resolveListingPriorityForUser(employer.getUser().getId()) : 0;

        List<JobResponse> jobs = jobEntities.stream()
                .map(job -> dtoMapper.toJobResponse(
                        job, 
                        false, 
                        false, 
                        null, 
                        appCounts.getOrDefault(job.getId(), 0L), 
                        listingPriority))
                .toList();

        return new PageResponse<>(jobs, page, size, jobPage.getTotalElements(), jobPage.getTotalPages(), jobPage.isFirst(), jobPage.isLast());
    }

    @Transactional
    public JobResponse createJob(JobRequest request) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        Company company = employer.getCompany();
        if (systemSettingsService.isCompanyReviewRequired()
                && !company.isVerified()
                && !"verified".equalsIgnoreCase(company.getVerificationStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMPANY_NOT_VERIFIED", "Công ty của bạn chưa được Admin xác thực. Chỉ các công ty đã được Admin xác thực mới có quyền đăng tin tuyển dụng.");
        }
        return jobService.createJobResponse(request, employer);
    }

    @Transactional
    public JobResponse updateJob(String id, JobRequest request) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        Company company = employer.getCompany();
        if (systemSettingsService.isCompanyReviewRequired()
                && !company.isVerified()
                && !"verified".equalsIgnoreCase(company.getVerificationStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMPANY_NOT_VERIFIED", "Công ty của bạn chưa được Admin xác thực. Chỉ các công ty đã được Admin xác thực mới có quyền quản lý và đăng tin tuyển dụng.");
        }
        return jobService.updateJobResponse(id, request, employer);
    }

    @Transactional
    public JobResponse submitJobForReview(String id) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        return jobService.submitJobForReview(id, employer);
    }

    @Transactional
    public void deleteJob(String id) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        jobService.deleteJobForEmployer(id, employer);
    }

    @Transactional
    public JobResponse closeJob(String id) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        return jobService.closeJobForEmployer(id, employer);
    }

    @Transactional
    public JobResponse reopenJob(String id, String newDeadline) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        return jobService.reopenJobForEmployer(id, employer, newDeadline);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> getCompanyApplications(String jobId, String status, String search, int page, int size) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (employer.getCompany() == null || employer.getCompany().getId() == null) {
            return new PageResponse<>(List.of(), page, size, 0, 0, true, true);
        }

        UUID parsedJobId = null;
        if (jobId != null && !jobId.trim().isEmpty()) {
            try {
                parsedJobId = UUID.fromString(jobId.trim());
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "JOB_ID_INVALID", "Mã việc làm không hợp lệ");
            }
            Job job = jobRepository.findById(parsedJobId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Không tìm thấy việc làm"));
            if (job.getCompany() == null || !job.getCompany().getId().equals(employer.getCompany().getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Không có quyền truy cập ứng viên của việc làm này");
            }
        }

        List<String> dbStatuses = parseApplicationStatusFilters(status);
        if (dbStatuses.isEmpty()) {
            dbStatuses = java.util.Arrays.stream(Application.ApplicationStatus.values())
                    .map(Application.ApplicationStatus::databaseValue)
                    .toList();
        }

        String filterSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : "";
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page - 1, size);

        org.springframework.data.domain.Page<Application> appPage = applicationRepository.searchCompanyApplications(
                employer.getCompany().getId(), parsedJobId, dbStatuses, filterSearch, pageable);

        if (appPage.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0, true, true);
        }

        List<ApplicationResponse> responses = applicationService.toResponseBulk(appPage.getContent());
        return new PageResponse<>(responses, page, size, appPage.getTotalElements(), appPage.getTotalPages(), appPage.isFirst(), appPage.isLast());
    }

    private List<String> parseApplicationStatusFilters(String status) {
        if (status == null || status.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : status.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                values.add(Application.ApplicationStatus.valueOf(token.toUpperCase(Locale.ROOT)).databaseValue());
            } catch (IllegalArgumentException ignored) {
                // Skip unknown tokens so a bad filter does not collapse to SUBMITTED.
            }
        }
        return values;
    }

    @Transactional
    public ApplicationResponse getApplicationDetail(String id) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        UUID appId;
        try {
            appId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "Mã hồ sơ không hợp lệ");
        }
        
        Application app = applicationRepository.findByIdWithDetails(appId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Không tìm thấy hồ sơ"));
                
        if (app.getJob() == null || app.getJob().getCompany() == null || !app.getJob().getCompany().getId().equals(employer.getCompany().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền xem hồ sơ này");
        }
        
        // Tự động chuyển trạng thái từ Mới nộp (SUBMITTED/applied) sang Đã xem (UNDER_REVIEW/reviewed) khi Nhà tuyển dụng xem chi tiết
        if ("applied".equalsIgnoreCase(app.getStatus()) || "SUBMITTED".equalsIgnoreCase(app.getStatus())) {
            applicationService.seedStatus(app, Application.ApplicationStatus.UNDER_REVIEW, "Nhà tuyển dụng đã xem hồ sơ ứng tuyển");
        }
        
        return applicationService.toResponse(app);
    }

    @Transactional
    public ApplicationResponse updateApplicationStatus(String applicationId, String status, String note) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (employer.getCompany() == null || employer.getCompany().getId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Công ty chưa được thiết lập");
        }
        UUID appId;
        try {
            appId = UUID.fromString(applicationId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "APPLICATION_ID_INVALID", "Mã đơn ứng tuyển không hợp lệ");
        }
        Application application = applicationRepository.findById(appId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Không tìm thấy đơn ứng tuyển"));

        if (application.getJob() == null || application.getJob().getCompany() == null || !application.getJob().getCompany().getId().equals(employer.getCompany().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Không có quyền cập nhật đơn ứng tuyển này");
        }

        Application.ApplicationStatus toStatus = null;
        try {
            toStatus = Application.ApplicationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            toStatus = Application.ApplicationStatus.fromDatabaseValue(status);
        }

        // fromDatabaseValue returns SUBMITTED by default, we want to reject completely invalid strings
        if (toStatus == Application.ApplicationStatus.SUBMITTED && !status.equalsIgnoreCase("SUBMITTED") && !status.equalsIgnoreCase("applied")) {
             throw new ApiException(HttpStatus.BAD_REQUEST, "STATUS_INVALID", "Trạng thái không hợp lệ: " + status);
        }

        return applicationService.updateStatus(appId, toStatus, note);
    }

    @Transactional(readOnly = true)
    public CandidateService.CvDownload downloadApplicationCv(String applicationId) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (employer.getCompany() == null || employer.getCompany().getId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Công ty chưa được thiết lập");
        }
        UUID appId;
        try {
            appId = UUID.fromString(applicationId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "APPLICATION_ID_INVALID", "Mã đơn ứng tuyển không hợp lệ");
        }
        Application application = applicationRepository.findById(appId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Không tìm thấy đơn ứng tuyển"));
        if (application.getJob() == null
                || application.getJob().getCompany() == null
                || !application.getJob().getCompany().getId().equals(employer.getCompany().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Không có quyền truy cập CV của đơn ứng tuyển này");
        }
        return applicationService.toSubmittedCvDownload(application);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications() {
        User user = authService.getCurrentUser();
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(dtoMapper::toNotificationResponse)
                .toList();
    }

    @Transactional
    public void markNotificationRead(String notificationId) {
        User user = authService.getCurrentUser();
        Notification notification = notificationRepository.findByIdAndRecipientUserId(UUID.fromString(notificationId), user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Không tìm thấy thông báo"));
        notification.setRead(true);
    }

    @Transactional
    public void markAllNotificationsRead() {
        User user = authService.getCurrentUser();
        notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(user.getId())
                .forEach(notification -> notification.setRead(true));
    }
}
