package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.JobRequest;
import com.sjp.recruitment.model.dto.response.CompanyLocationResponse;
import com.sjp.recruitment.model.dto.response.CompanyResponse;
import com.sjp.recruitment.model.dto.response.JobPageResponse;
import com.sjp.recruitment.model.dto.response.JobResponse;
import com.sjp.recruitment.model.dto.response.RecommendationResponse;
import com.sjp.recruitment.model.dto.response.UserResponse;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.Company;
import com.sjp.recruitment.model.entity.CompanyLocation;
import com.sjp.recruitment.model.entity.Employer;
import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.model.entity.ApplicationStatusHistory;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.model.entity.Notification;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.model.entity.Skill;
import com.sjp.recruitment.model.entity.JobSkill;
import com.sjp.recruitment.model.entity.JobEditHistory;
import com.sjp.recruitment.model.dto.JobSnapshot;
import com.sjp.recruitment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyLocationRepository companyLocationRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final SavedJobRepository savedJobRepository;
    private final ApplicationRepository applicationRepository;
    private final SkillRepository skillRepository;
    private final JobSkillRepository jobSkillRepository;
    private final NotificationRepository notificationRepository;
    private final ApplicationStatusHistoryRepository applicationStatusHistoryRepository;
    private final JobEditHistoryRepository jobEditHistoryRepository;
    private final InterviewScheduleRepository interviewScheduleRepository;
    private final DtoMapper dtoMapper;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final SystemSettingsService systemSettingsService;
    private final FeatureLimitService featureLimitService;
    private final AuthService authService;
    private final CandidateRealtimeEventPublisher realtimeEventPublisher;

    @Transactional(readOnly = true)
    public JobPageResponse search(String search, String location, BigDecimal minSalary, BigDecimal maxSalary,
                                  String experienceLevel, String skills, String category, String jobType,
                                  String workMode, String sort, int page, int size) {
        if (minSalary != null && maxSalary != null && minSalary.compareTo(maxSalary) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SALARY_RANGE_INVALID", "Mức lương tối thiểu không thể lớn hơn mức lương tối đa");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", safeSize)
                .addValue("offset", (long) safePage * safeSize);

        String whereClause = buildRemoteJobWhereClause(
                search, location, minSalary, maxSalary, experienceLevel, skills, category, jobType, workMode, params);

        String countSql = "SELECT COUNT(*) FROM jobs j JOIN companies c ON c.id = j.company_id\n" + whereClause;
        Long totalElements = namedParameterJdbcTemplate.queryForObject(countSql, params, Long.class);
        long total = totalElements == null ? 0 : totalElements;
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) safeSize);
        if (totalPages > 0 && safePage >= totalPages) {
            safePage = totalPages - 1;
            params.addValue("offset", (long) safePage * safeSize);
        }

        String dataSql = """
                SELECT
                    j.id::text AS id,
                    j.title,
                    j.description,
                    j.requirements,
                    j.benefits,
                    j.vacancies,
                    j.working_time,
                    j.salary_type,
                    j.job_type,
                    j.work_mode,
                    j.views_count,
                    j.salary_min,
                    j.salary_max,
                    j.location,
                    j.experience_level,
                    j.deadline,
                    j.status,
                    j.rejection_reason,
                    j.company_location_id::text AS company_location_id,
                    cl.branch_name AS cl_branch_name,
                    cl.address AS cl_address,
                    cl.city AS cl_city,
                    cl.district AS cl_district,
                    cl.country AS cl_country,
                    cl.is_headquarter AS cl_is_headquarter,
                    c.id::text AS company_id,
                    c.name AS company_name,
                    c.website AS company_website,
                    c.location AS company_location,
                    c.logo_url AS company_logo_url,
                    COALESCE(array_remove(array_agg(DISTINCT s.name), NULL), ARRAY[]::text[]) AS skills,
                    __LISTING_PRIORITY__ AS listing_priority
                FROM jobs j
                JOIN companies c ON c.id = j.company_id
                LEFT JOIN company_locations cl ON cl.id = j.company_location_id
                LEFT JOIN job_skills js ON js.job_id = j.id
                LEFT JOIN skills s ON s.id = js.skill_id
                """
                + whereClause
                + """
                GROUP BY
                    j.id, j.title, j.description, j.requirements, j.benefits, j.vacancies, j.working_time, j.salary_type, j.job_type, j.work_mode, j.views_count, j.salary_min, j.salary_max,
                    j.location, j.experience_level, j.deadline, j.status, j.rejection_reason, j.company_location_id, j.created_by_employer_id,
                    cl.branch_name, cl.address, cl.city, cl.district, cl.country, cl.is_headquarter,
                    c.id, c.name, c.website, c.location, c.logo_url
                """
                + resolveRemoteJobOrder(sort)
                + " LIMIT :limit OFFSET :offset";
        dataSql = dataSql.replace("__LISTING_PRIORITY__", FeatureLimitService.LISTING_PRIORITY_SQL.trim());

        List<JobResponse> content = namedParameterJdbcTemplate.query(dataSql, params, this::mapRemoteJobResponse);
        CandidateProfile candidate = currentCandidate().orElse(null);
        if (candidate != null && !content.isEmpty()) {
            List<UUID> jobIds = content.stream().map(job -> UUID.fromString(job.id())).toList();
            Set<UUID> savedIds = new HashSet<>(savedJobRepository.findSavedJobIds(candidate.getId(), jobIds));
            Set<UUID> appliedIds = new HashSet<>(applicationRepository.findAppliedJobIds(candidate.getId(), jobIds));
            content = content.stream()
                    .map(job -> withCandidateState(job, candidate, savedIds, appliedIds))
                    .toList();
        }
        return new JobPageResponse(content, safePage, safeSize, total, totalPages);
    }

    @Transactional
    public JobResponse findJobResponseById(String id) {
        return findJobResponseById(id, true);
    }

    @Transactional(readOnly = true)
    public JobResponse findPublicJobResponseByIdWithoutViewIncrement(String id) {
        return findJobResponseById(id, false);
    }

    private JobResponse findJobResponseById(String id, boolean incrementView) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "JOB_ID_INVALID", "Ma viec lam khong hop le");
        }

        if (incrementView) {
            namedParameterJdbcTemplate.update(
                    "UPDATE jobs SET views_count = views_count + 1 WHERE id = CAST(:id AS uuid) AND status = 'published'",
                    new MapSqlParameterSource("id", id)
            );
        }

        String sql = """
                SELECT
                    j.id::text AS id,
                    j.title,
                    j.description,
                    j.requirements,
                    j.benefits,
                    j.vacancies,
                    j.working_time,
                    j.salary_type,
                    j.job_type,
                    j.work_mode,
                    j.views_count,
                    j.salary_min,
                    j.salary_max,
                    j.location,
                    j.experience_level,
                    j.deadline,
                    j.status,
                    j.rejection_reason,
                    j.company_location_id::text AS company_location_id,
                    cl.branch_name AS cl_branch_name,
                    cl.address AS cl_address,
                    cl.city AS cl_city,
                    cl.district AS cl_district,
                    cl.country AS cl_country,
                    cl.is_headquarter AS cl_is_headquarter,
                    c.id::text AS company_id,
                    c.name AS company_name,
                    c.website AS company_website,
                    c.location AS company_location,
                    c.logo_url AS company_logo_url,
                    COALESCE(array_remove(array_agg(DISTINCT s.name), NULL), ARRAY[]::text[]) AS skills,
                    __LISTING_PRIORITY__ AS listing_priority
                FROM jobs j
                JOIN companies c ON c.id = j.company_id
                LEFT JOIN company_locations cl ON cl.id = j.company_location_id
                LEFT JOIN job_skills js ON js.job_id = j.id
                LEFT JOIN skills s ON s.id = js.skill_id
                WHERE j.id = CAST(:id AS uuid)
                  AND j.status = 'published'
                GROUP BY
                    j.id, j.title, j.description, j.requirements, j.benefits, j.vacancies, j.working_time, j.salary_type, j.job_type, j.work_mode, j.views_count, j.salary_min, j.salary_max,
                    j.location, j.experience_level, j.deadline, j.status, j.rejection_reason, j.company_location_id, j.created_by_employer_id,
                    cl.branch_name, cl.address, cl.city, cl.district, cl.country, cl.is_headquarter,
                    c.id, c.name, c.website, c.location, c.logo_url
                """.replace("__LISTING_PRIORITY__", FeatureLimitService.LISTING_PRIORITY_SQL.trim());
        CandidateProfile candidate = currentCandidate().orElse(null);
        List<JobResponse> jobs = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource("id", id),
                (resultSet, rowNumber) -> mapRemoteJobResponse(resultSet, rowNumber, candidate)
        );
        return jobs.stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Khong tim thay viec lam"));
    }

    @Transactional(readOnly = true)
    public Job findById(String id) {
        return jobRepository.findById(parseUuid(id, "JOB_ID_INVALID"))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Khong tim thay viec lam"));
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> recommendations() {
        CandidateProfile candidate = currentCandidate()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "CANDIDATE_REQUIRED", "Chi ung vien moi co goi y viec lam"));
        Set<String> candidateSkills = normalized(candidate.getSkills());
        boolean lowConfidence = candidateSkills.isEmpty();
        List<UUID> jobIds = jobRepository.findRecommendationJobIds(PageRequest.of(0, 20));
        if (jobIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Job> jobsById = jobRepository.findRecommendationJobsByIds(jobIds).stream()
                .collect(Collectors.toMap(Job::getId, job -> job));
        Set<UUID> savedJobIds = new HashSet<>(savedJobRepository.findSavedJobIds(candidate.getId(), jobIds));
        Set<UUID> appliedJobIds = new HashSet<>(applicationRepository.findAppliedJobIds(candidate.getId(), jobIds));
        Map<UUID, Long> applicationCounts = applicationRepository.countByJobIds(jobIds).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
        Map<UUID, Integer> listingPriorities = featureLimitService.resolveListingPrioritiesForUsers(
                jobsById.values().stream().map(this::employerUserId).filter(Objects::nonNull).toList()
        );

        return jobIds.stream()
                .map(jobsById::get)
                .filter(Objects::nonNull)
                .map(job -> toRecommendation(
                        candidate,
                        candidateSkills,
                        job,
                        lowConfidence,
                        savedJobIds.contains(job.getId()),
                        appliedJobIds.contains(job.getId()),
                        applicationCounts.getOrDefault(job.getId(), 0L),
                        listingPriorities.getOrDefault(employerUserId(job), 0)
                ))
                .sorted(Comparator
                        .comparingInt(RecommendationResponse::matchScore).reversed()
                        .thenComparing((RecommendationResponse r) ->
                                        r.job() != null && r.job().listingPriority() != null
                                                ? r.job().listingPriority()
                                                : 0,
                                Comparator.reverseOrder()))
                .limit(10)
                .toList();
    }

    @Transactional
    public Job create(JobRequest request, Employer employer) {
        if (employer == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMPLOYER_REQUIRED", "Khong tim thay tai khoan nha tuyen dung");
        }
        Company company = employer.getCompany();
        if (company == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMPANY_REQUIRED", "Cần có công ty trước khi tạo việc làm");
        }
        if (systemSettingsService.isCompanyReviewRequired()
                && !company.isVerified()
                && !"verified".equalsIgnoreCase(company.getVerificationStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMPANY_NOT_VERIFIED", "Công ty của bạn chưa được Admin xác thực. Chỉ các công ty đã được Admin xác thực mới có quyền đăng tin tuyển dụng.");
        }
        User currentUser = authService.getCurrentUser();
        featureLimitService.requireJobPost(currentUser);
        Job saved = buildAndSaveJob(new Job(), employer, company, request);
        featureLimitService.consumeJobPost(currentUser);
        return saved;
    }

    @Transactional
    public JobResponse createJobResponse(JobRequest request, Employer employer) {
        Job job = create(request, employer);
        return dtoMapper.toJobResponse(job, false, false, null);
    }

    @Transactional
    public Job update(String id, JobRequest request, Employer employer) {
        Job job = findById(id);
        checkEmployerPermission(job, employer, "Ban khong co quyen cap nhat viec lam nay");
        if (systemSettingsService.isCompanyReviewRequired()
                && job.getCompany() != null
                && (!job.getCompany().isVerified() && !"verified".equalsIgnoreCase(job.getCompany().getVerificationStatus()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMPANY_NOT_VERIFIED", "Công ty của bạn chưa được Admin xác thực.");
        }

        long applicationCount = applicationRepository.countByJobId(job.getId());
        JobSnapshot oldSnapshot = null;
        if (applicationCount > 0) {
            oldSnapshot = JobSnapshot.fromJob(job);
        }

        Job updatedJob = buildAndSaveJob(job, job.getEmployer(), job.getCompany(), request);

        if (applicationCount > 0 && oldSnapshot != null) {
            JobSnapshot newSnapshot = JobSnapshot.fromJob(updatedJob);
            compareAndLogAndNotify(updatedJob, oldSnapshot, newSnapshot, authService.getCurrentUser());
            applicationRepository.markApplicationsForRerank(updatedJob.getId());
        }

        return updatedJob;
    }

    private void compareAndLogAndNotify(Job job, JobSnapshot oldSnap, JobSnapshot newSnap, User editedBy) {
        boolean sensitiveChanged = false;

        sensitiveChanged |= checkAndLog(job, editedBy, "title", oldSnap.getTitle(), newSnap.getTitle(), true);
        sensitiveChanged |= checkAndLog(job, editedBy, "description", oldSnap.getDescription(), newSnap.getDescription(), true);
        sensitiveChanged |= checkAndLog(job, editedBy, "requirements", joinList(oldSnap.getRequirements()), joinList(newSnap.getRequirements()), true);
        sensitiveChanged |= checkAndLog(job, editedBy, "salaryMin", toString(oldSnap.getSalaryMin()), toString(newSnap.getSalaryMin()), true);
        sensitiveChanged |= checkAndLog(job, editedBy, "salaryMax", toString(oldSnap.getSalaryMax()), toString(newSnap.getSalaryMax()), true);
        sensitiveChanged |= checkAndLog(job, editedBy, "location", oldSnap.getLocation(), newSnap.getLocation(), true);

        checkAndLog(job, editedBy, "benefits", oldSnap.getBenefits(), newSnap.getBenefits(), false);
        checkAndLog(job, editedBy, "vacancies", toString(oldSnap.getVacancies()), toString(newSnap.getVacancies()), false);
        checkAndLog(job, editedBy, "workingTime", oldSnap.getWorkingTime(), newSnap.getWorkingTime(), false);
        checkAndLog(job, editedBy, "salaryType", oldSnap.getSalaryType(), newSnap.getSalaryType(), false);
        checkAndLog(job, editedBy, "currency", oldSnap.getCurrency(), newSnap.getCurrency(), false);
        checkAndLog(job, editedBy, "jobType", oldSnap.getJobType(), newSnap.getJobType(), false);
        checkAndLog(job, editedBy, "workMode", oldSnap.getWorkMode(), newSnap.getWorkMode(), false);
        checkAndLog(job, editedBy, "experienceLevel", oldSnap.getExperienceLevel(), newSnap.getExperienceLevel(), false);
        checkAndLog(job, editedBy, "deadline", toString(oldSnap.getDeadline()), toString(newSnap.getDeadline()), false);

        if (sensitiveChanged) {
            List<Application> applications = applicationRepository.findAllByJobId(job.getId());
            if (applications != null) {
                for (Application app : applications) {
                    if (app.getStatusEnum() != Application.ApplicationStatus.WITHDRAWN
                        && app.getStatusEnum() != Application.ApplicationStatus.REJECTED) {
                        try {
                            Notification note = new Notification();
                            note.setRecipientUser(app.getCandidate().getUser());
                            note.setType("JOB_UPDATED");
                            note.setTitle("Thông báo thay đổi tin tuyển dụng");
                            note.setMessage("Tin tuyển dụng [" + job.getTitle() + "] bạn đã ứng tuyển vừa có sự thay đổi. Vui lòng kiểm tra lại thông tin để đảm bảo quyền lợi của bạn.");
                            note.setRelatedEntityType("JOB");
                            note.setRelatedEntityId(job.getId());
                            Notification saved = notificationRepository.save(note);
                            realtimeEventPublisher.publishAfterCommit(
                                    app.getCandidate().getUser(), "NOTIFICATION_UPDATED", saved.getId());
                        } catch (Exception ex) {
                            // ignore individual fail
                        }
                    }
                }
            }
        }
    }

    private boolean checkAndLog(Job job, User editedBy, String fieldName, String oldVal, String newVal, boolean isSensitive) {
        if (!Objects.equals(oldVal, newVal)) {
            JobEditHistory history = new JobEditHistory();
            history.setJob(job);
            history.setEditedByUser(editedBy);
            history.setFieldName(fieldName);
            history.setOldValue(oldVal);
            history.setNewValue(newVal);
            jobEditHistoryRepository.save(history);
            return isSensitive;
        }
        return false;
    }

    private String toString(Object obj) {
        return obj == null ? null : obj.toString();
    }

    private String joinList(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return String.join("\n", list);
    }

    @Transactional
    public JobResponse updateJobResponse(String id, JobRequest request, Employer employer) {
        Job job = update(id, request, employer);
        return dtoMapper.toJobResponse(job, false, false, null);
    }

    @Transactional
    public JobResponse submitJobForReview(String id, Employer employer) {
        Job job = findById(id);
        checkEmployerPermission(job, employer, "Bạn không có quyền thao tác với việc làm này");
        if (systemSettingsService.isCompanyReviewRequired()
                && job.getCompany() != null
                && (!job.getCompany().isVerified() && !"verified".equalsIgnoreCase(job.getCompany().getVerificationStatus()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMPANY_NOT_VERIFIED", "Công ty của bạn chưa được Admin xác thực pháp lý.");
        }
        if ("rejected".equalsIgnoreCase(job.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "JOB_REJECTED", "Tin tuyển dụng đã bị từ chối duyệt. Vui lòng chỉnh sửa nội dung tin tuyển dụng trước khi gửi duyệt lại.");
        }

        boolean fromReportFix = "awaiting_company".equalsIgnoreCase(job.getStatus());
        if (fromReportFix || !hasCompanyHadApprovedJob(job.getCompany(), job.getId())) {
            job.setStatus("pending_review");
            // Giữ rejection_reason để admin còn thấy ghi chú báo cáo; chỉ clear closed_at
            job.setClosedAt(null);
        } else {
            job.setStatus("published");
            if (job.getPublishedAt() == null) {
                job.setPublishedAt(LocalDateTime.now());
            }
            if (job.getPostedAt() == null) {
                job.setPostedAt(LocalDateTime.now());
            }
            job.setRejectionReason(null);
            job.setClosedAt(null);
        }
        job = jobRepository.save(job);

        if (fromReportFix) {
            namedParameterJdbcTemplate.update("""
                    UPDATE job_reports
                    SET status = 'resubmitted',
                        company_fix_deadline = NULL,
                        resolved_at = now()
                    WHERE job_id = CAST(:jobId AS uuid)
                      AND status = 'awaiting_company'
                    """,
                    new MapSqlParameterSource("jobId", job.getId().toString())
            );
            job.setReportFixDeadline(null);
            job = jobRepository.save(job);
        }

        return dtoMapper.toJobResponse(job, false, false, null);
    }

    @Transactional
    public void delete(String id) {
        UUID jobId = parseUuid(id, "JOB_ID_INVALID");
        jobSkillRepository.deleteByJobId(jobId);
        jobRepository.deleteById(jobId);
    }

    private void checkEmployerPermission(Job job, Employer employer, String errorMessage) {
        if (job == null || employer == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", errorMessage);
        }
        boolean byEmployer = job.getEmployer() != null && job.getEmployer().getId() != null && job.getEmployer().getId().equals(employer.getId());
        boolean byCompany = job.getCompany() != null && job.getCompany().getId() != null && employer.getCompany() != null && job.getCompany().getId().equals(employer.getCompany().getId());
        if (!byEmployer && !byCompany) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", errorMessage);
        }
    }

    @Transactional
    public void deleteJobForEmployer(String id, Employer employer) {
        Job job = findById(id);
        checkEmployerPermission(job, employer, "Bạn không có quyền xóa việc làm này");

        String status = job.getStatus();

        // ── 1. Trạng thái KHÔNG cho phép xoá ──
        if ("pending_review".equalsIgnoreCase(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "JOB_PENDING_REVIEW",
                    "Tin đang chờ Admin duyệt. Vui lòng chờ kết quả duyệt hoặc rút lại tin trước khi xóa.");
        }
        if ("awaiting_company".equalsIgnoreCase(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "JOB_AWAITING_COMPANY",
                    "Tin đang chờ bạn chỉnh sửa theo yêu cầu của Admin. Vui lòng xử lý trước khi xóa.");
        }
        if ("removed".equalsIgnoreCase(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "JOB_REMOVED",
                    "Tin đã bị Admin gỡ bỏ do vi phạm. Không thể thực hiện thao tác xóa.");
        }
        if ("archived".equalsIgnoreCase(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "JOB_ALREADY_ARCHIVED",
                    "Tin đã được lưu trữ (archived) trước đó.");
        }

        // ── 2. Thu thập applications ──
        List<Application> applications = applicationRepository.findAllByJobId(job.getId());
        boolean hasApplications = applications != null && !applications.isEmpty();

        // ── 3. Nếu có ứng viên → kiểm tra lịch phỏng vấn active ──
        if (hasApplications) {
            List<UUID> appIds = applications.stream()
                    .map(Application::getId)
                    .collect(Collectors.toList());
            boolean hasActiveInterviews = interviewScheduleRepository
                    .existsActiveByApplicationIds(appIds);
            if (hasActiveInterviews) {
                throw new ApiException(HttpStatus.CONFLICT, "JOB_HAS_ACTIVE_INTERVIEWS",
                        "Tin tuyển dụng đang có lịch phỏng vấn chưa hoàn tất. "
                        + "Vui lòng hoàn thành hoặc hủy phỏng vấn trước khi xóa.");
            }
        }

        // ── 4. Không có ứng viên → Hard delete ──
        if (!hasApplications) {
            jobRepository.delete(job);
            return;
        }

        // ── 5. Có ứng viên → Soft delete (archive) ──
        archiveJobAndRejectPendingApplications(job, applications);
    }

    /**
     * Soft-delete: chuyển job sang "archived" và tự động reject các đơn ứng tuyển
     * đang pending, ghi lịch sử và gửi notification cho ứng viên.
     */
    private void archiveJobAndRejectPendingApplications(Job job, List<Application> applications) {
        job.setStatus("archived");
        if (job.getClosedAt() == null) {
            job.setClosedAt(LocalDateTime.now());
        }
        jobRepository.save(job);

        for (Application app : applications) {
            if (app == null || app.getStatusEnum() == null) continue;

            Application.ApplicationStatus currentStatus = app.getStatusEnum();
            boolean isPending = currentStatus == Application.ApplicationStatus.SUBMITTED
                    || currentStatus == Application.ApplicationStatus.UNDER_REVIEW
                    || currentStatus == Application.ApplicationStatus.SHORTLISTED
                    || currentStatus == Application.ApplicationStatus.INTERVIEW_SCHEDULED;

            if (isPending) {
                app.setStatus(Application.ApplicationStatus.REJECTED);
                if (app.getReviewedAt() == null) {
                    app.setReviewedAt(LocalDateTime.now());
                }
                applicationRepository.save(app);

                try {
                    ApplicationStatusHistory history = new ApplicationStatusHistory();
                    history.setApplication(app);
                    history.setFromStatus(currentStatus);
                    history.setToStatus(Application.ApplicationStatus.REJECTED);
                    history.setPublicNote("Tin tuyển dụng đã bị nhà tuyển dụng xóa (lưu trữ). "
                            + "Đơn ứng tuyển tự động chuyển sang trạng thái Từ chối.");
                    applicationStatusHistoryRepository.save(history);
                } catch (Exception ex) {
                    // non-critical — ghi log thất bại không làm rollback transaction
                }

                sendJobDeletedNotification(app, job,
                        "Tin tuyển dụng [" + job.getTitle()
                        + "] mà bạn ứng tuyển đã bị nhà tuyển dụng xóa (lưu trữ). "
                        + "Đơn ứng tuyển của bạn đã tự động chuyển sang trạng thái Từ chối.");
            } else {
                sendJobDeletedNotification(app, job,
                        "Tin tuyển dụng [" + job.getTitle()
                        + "] mà bạn đã ứng tuyển vừa được nhà tuyển dụng lưu trữ (archive).");
            }
        }
    }

    private void sendJobDeletedNotification(Application app, Job job, String message) {
        if (app.getCandidate() == null || app.getCandidate().getUser() == null) return;
        try {
            Notification note = new Notification();
            note.setRecipientUser(app.getCandidate().getUser());
            note.setType("JOB_DELETED");
            note.setTitle("Thông báo tin tuyển dụng bị xóa/lưu trữ");
            note.setMessage(message);
            note.setRelatedEntityType("JOB");
            note.setRelatedEntityId(job.getId());
            Notification saved = notificationRepository.save(note);
            realtimeEventPublisher.publishAfterCommit(
                    app.getCandidate().getUser(), "NOTIFICATION_UPDATED", saved.getId());
        } catch (Exception ex) {
            // non-critical — gửi notification thất bại không làm rollback transaction
        }
    }

    @Transactional
    public JobResponse closeJobForEmployer(String id, Employer employer) {
        Job job = findById(id);
        checkEmployerPermission(job, employer, "Bạn không có quyền thao tác với việc làm này");
        if ("closed".equalsIgnoreCase(job.getStatus())) {
            return dtoMapper.toJobResponse(job, false, false, null);
        }
        job.setStatus("closed");
        if (job.getClosedAt() == null) {
            job.setClosedAt(LocalDateTime.now());
        }
        job = jobRepository.save(job);
        notifyCandidatesJobClosed(job, "Tin tuyển dụng [" + job.getTitle() + "] mà bạn nộp đơn ứng tuyển đã được nhà tuyển dụng đóng (ngừng nhận đơn).");
        return dtoMapper.toJobResponse(job, false, false, null);
    }

    @Transactional
    public JobResponse reopenJobForEmployer(String id, Employer employer, String newDeadline) {
        Job job = findById(id);
        checkEmployerPermission(job, employer, "Bạn không có quyền thao tác với việc làm này");
        if (job.getCompany() != null && (!job.getCompany().isVerified() && !"verified".equalsIgnoreCase(job.getCompany().getVerificationStatus()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMPANY_NOT_VERIFIED", "Công ty của bạn chưa được Admin xác thực.");
        }
        String previousStatus = job.getStatus() == null ? "" : job.getStatus().toLowerCase();
        boolean wasInactive = List.of("closed", "expired", "archived", "removed").contains(previousStatus);
        if (wasInactive) {
            // Tin đóng/hết hạn không nằm trong quota hiện tại → mở lại phải còn slot
            featureLimitService.requireJobPost(authService.getCurrentUser());
        }
        if (newDeadline != null && !newDeadline.isBlank()) {
            try {
                job.setDeadline(LocalDate.parse(newDeadline));
            } catch (Exception e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEADLINE", "Hạn nộp hồ sơ mới không đúng định dạng (YYYY-MM-DD)");
            }
        }
        if (job.getDeadline() != null && job.getDeadline().isBefore(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEADLINE_EXPIRED", "Tin tuyển dụng đã hết hạn nộp hồ sơ. Vui lòng cập nhật hạn nộp hồ sơ mới trước khi mở lại tin.");
        }
        long acceptedCount = applicationRepository.countByJobIdAndStatus(job.getId(), "accepted");
        if (job.getVacancies() != null && acceptedCount >= job.getVacancies()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VACANCIES_FILLED", "Tin tuyển dụng đã tuyển đủ số lượng chỉ tiêu (" + acceptedCount + "/" + job.getVacancies() + "). Vui lòng tăng số lượng tuyển dụng trước khi mở lại tin.");
        }
        job.setStatus("published");
        job.setClosedAt(null);
        job = jobRepository.save(job);
        return dtoMapper.toJobResponse(job, false, false, null);
    }

    @Transactional
    public void notifyCandidatesJobClosed(Job job, String message) {
        if (job == null || job.getId() == null) return;
        try {
            List<Application> applications = applicationRepository.findAllByJobId(job.getId());
            if (applications == null) return;
            for (Application app : applications) {
                if (app != null && app.getCandidate() != null && app.getCandidate().getUser() != null) {
                    try {
                        Notification note = new Notification();
                        note.setRecipientUser(app.getCandidate().getUser());
                        note.setType("JOB_CLOSED");
                        note.setTitle("Thông báo đóng tin tuyển dụng");
                        note.setMessage(message);
                        note.setRelatedEntityType("JOB");
                        note.setRelatedEntityId(job.getId());
                        Notification saved = notificationRepository.save(note);
                        realtimeEventPublisher.publishAfterCommit(
                                app.getCandidate().getUser(), "NOTIFICATION_UPDATED", saved.getId());
                    } catch (Exception ex) {
                        // ignore single notification save failure so transaction completes
                    }
                }
            }
        } catch (Exception e) {
            // ignore overall notification save failure so transaction completes
        }
    }

    private Job buildAndSaveJob(Job job, Employer employer, Company company, JobRequest request) {
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setRequirements(request.getRequirements() == null ? List.of() : request.getRequirements());
        job.setBenefits(request.getBenefits());
        job.setSalaryMin(request.getSalaryMin());
        job.setSalaryMax(request.getSalaryMax());
        job.setSalaryType(request.getSalaryType() != null ? request.getSalaryType() : (request.getSalaryMin() != null && request.getSalaryMax() != null ? "range" : "negotiable"));
        job.setVacancies(request.getVacancies() != null && request.getVacancies() > 0 ? request.getVacancies() : 1);
        job.setWorkingTime(request.getWorkingTime());
        job.setJobType(request.getJobType() != null ? request.getJobType() : "full_time");
        job.setWorkMode(request.getWorkMode() != null ? request.getWorkMode() : "onsite");
        job.setExperienceLevel(request.getExperienceLevel() != null ? request.getExperienceLevel() : "fresher");
        
        if (request.getRankingConfig() != null && request.getRankingConfig().has("enabled") && request.getRankingConfig().get("enabled").asBoolean()) {
            if (!featureLimitService.hasActivePaidPlan(authService.getCurrentUser())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "PLAN_UPGRADE_REQUIRED", "Tính năng Smart Ranking yêu cầu gói dịch vụ nâng cao.");
            }
        }
        job.setRankingConfig(request.getRankingConfig());

        if (request.getDeadline() != null && !request.getDeadline().isBlank()) {
            try {
                job.setDeadline(LocalDate.parse(request.getDeadline()));
            } catch (Exception e) {
                job.setDeadline(LocalDate.now().plusDays(30));
            }
        } else if (job.getDeadline() == null) {
            job.setDeadline(LocalDate.now().plusDays(30));
        }

        String targetStatus = request.getStatus() != null && !request.getStatus().isBlank() ? request.getStatus().toLowerCase() : "draft";
        boolean wasNotClosed = !"closed".equalsIgnoreCase(job.getStatus());
        boolean fromReportFix = "awaiting_company".equalsIgnoreCase(job.getStatus());

        if ("pending_review".equals(targetStatus) || "published".equals(targetStatus) || "active".equals(targetStatus)) {
            if (fromReportFix) {
                // Tin bị báo cáo → công ty sửa xong phải qua admin duyệt lại, không auto-publish
                targetStatus = "pending_review";
                job.setClosedAt(null);
            } else if (hasCompanyHadApprovedJob(company, job.getId())) {
                targetStatus = "published";
                if (job.getPublishedAt() == null) {
                    job.setPublishedAt(LocalDateTime.now());
                }
                if (job.getPostedAt() == null) {
                    job.setPostedAt(LocalDateTime.now());
                }
                job.setRejectionReason(null);
                job.setClosedAt(null);
            } else {
                targetStatus = "pending_review";
                job.setRejectionReason(null);
                job.setClosedAt(null);
            }
        } else if (fromReportFix && ("draft".equals(targetStatus) || "awaiting_company".equals(targetStatus))) {
            targetStatus = "awaiting_company";
        }
        job.setStatus(targetStatus);
        if ("closed".equals(targetStatus) && job.getClosedAt() == null) {
            job.setClosedAt(LocalDateTime.now());
        }
        if ("published".equals(targetStatus) && job.getPublishedAt() == null) {
            job.setPublishedAt(LocalDateTime.now());
        }
        if (job.getPostedAt() == null) {
            job.setPostedAt(LocalDateTime.now());
        }

        if (request.getCompanyLocationId() != null && !request.getCompanyLocationId().isBlank()) {
            CompanyLocation loc = companyLocationRepository.findById(parseUuid(request.getCompanyLocationId(), "LOCATION_ID_INVALID"))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND", "Không tìm thấy địa điểm làm việc"));
            if (loc.getCompany() == null || company == null || !loc.getCompany().getId().equals(company.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "LOCATION_FORBIDDEN", "Dia diem lam viec khong thuoc cong ty cua ban");
            }
            job.setCompanyLocation(loc);
            job.setLocation(loc.getBranchName());
        } else {
            job.setLocation(request.getLocation() != null ? request.getLocation() : (company != null ? company.getLocation() : "Hà Nội"));
        }

        job.setEmployer(employer);
        job.setCompany(company);
        job = jobRepository.save(job);

        if (fromReportFix && "pending_review".equals(targetStatus) && job.getId() != null) {
            namedParameterJdbcTemplate.update("""
                    UPDATE job_reports
                    SET status = 'resubmitted',
                        company_fix_deadline = NULL,
                        resolved_at = now()
                    WHERE job_id = CAST(:jobId AS uuid)
                      AND status = 'awaiting_company'
                    """,
                    new MapSqlParameterSource("jobId", job.getId().toString())
            );
            job.setReportFixDeadline(null);
            job = jobRepository.save(job);
        }

        if ("closed".equals(targetStatus) && wasNotClosed && job.getId() != null) {
            notifyCandidatesJobClosed(job, "Tin tuyển dụng [" + job.getTitle() + "] mà bạn nộp đơn ứng tuyển đã được nhà tuyển dụng đóng (ngừng nhận đơn).");
        }

        jobSkillRepository.deleteByJobId(job.getId());
        job.setJobSkills(new ArrayList<>());
        List<String> skillNames = request.getSkills() != null && !request.getSkills().isEmpty()
                ? request.getSkills()
                : (request.getRequirements() == null ? List.of() : request.getRequirements());
        Job finalJob = job;
        Set<UUID> addedSkillIds = new HashSet<>();
        skillNames.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(skillName -> {
                    Skill skill = skillRepository.findByNameIgnoreCase(skillName)
                            .orElseGet(() -> {
                                String slug = slugifySkill(skillName);
                                return skillRepository.findBySlug(slug).orElseGet(() -> {
                                    Skill created = new Skill();
                                    created.setName(skillName);
                                    created.setSlug(slug);
                                    created.setCategory("General");
                                    return skillRepository.save(created);
                                });
                            });
                    if (skill != null && skill.getId() != null && addedSkillIds.add(skill.getId())) {
                        JobSkill jobSkill = new JobSkill();
                        jobSkill.setJob(finalJob);
                        jobSkill.setSkill(skill);
                        jobSkill.setRequired(true);
                        jobSkill = jobSkillRepository.save(jobSkill);
                        finalJob.getJobSkills().add(jobSkill);
                    }
                });

        return job;
    }

    private boolean hasCompanyHadApprovedJob(Company company, UUID excludeJobId) {
        if (company == null || company.getId() == null) {
            return false;
        }
        List<Job> companyJobs = jobRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId());
        if (companyJobs == null) {
            return false;
        }
        for (Job j : companyJobs) {
            if (excludeJobId != null && excludeJobId.equals(j.getId())) {
                continue;
            }
            if (j.getPublishedAt() != null || "published".equalsIgnoreCase(j.getStatus())
                    || "active".equalsIgnoreCase(j.getStatus()) || "closed".equalsIgnoreCase(j.getStatus())
                    || "expired".equalsIgnoreCase(j.getStatus()) || "archived".equalsIgnoreCase(j.getStatus())) {
                return true;
            }
        }
        return false;
    }

    private String slugifySkill(String value) {
        String slug = value.toLowerCase(Locale.ROOT)
                .replace("c++", "cplusplus")
                .replace("c#", "csharp")
                .replace(".net", "dotnet")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "skill-" + UUID.randomUUID().toString().substring(0, 8) : slug;
    }

    @Transactional(readOnly = true)
    public Page<Job> findByEmployerId(String employerId, Pageable pageable) {
        return jobRepository.findByEmployerId(parseUuid(employerId, "EMPLOYER_ID_INVALID"), pageable);
    }

    public JobResponse toJobResponse(Job job, CandidateProfile candidate) {
        boolean saved = candidate != null && savedJobRepository.existsByCandidateIdAndJobId(candidate.getId(), job.getId());
        boolean applied = candidate != null && applicationRepository.existsByCandidateIdAndJobId(candidate.getId(), job.getId());
        return dtoMapper.toJobResponse(job, saved, applied, null);
    }

    public int calculateMatchScore(CandidateProfile candidate, Job job) {
        return calculateMatchScore(normalized(candidate.getSkills()), candidate.getLocation(), job);
    }

    private int calculateMatchScore(Set<String> candidateSkills, String candidateLocation, Job job) {
        Set<String> jobSkills = normalized(job.getSkills() == null || job.getSkills().isEmpty() ? job.getRequirements() : job.getSkills());
        if (candidateSkills.isEmpty() || jobSkills.isEmpty()) {
            return 20;
        }
        long matches = jobSkills.stream().filter(candidateSkills::contains).count();
        int skillScore = (int) Math.round((matches * 70.0) / jobSkills.size());
        int locationScore = candidateLocation != null && job.getLocation() != null
                && job.getLocation().toLowerCase().contains(candidateLocation.toLowerCase()) ? 20 : 0;
        int base = matches > 0 ? 10 : 0;
        return Math.min(100, skillScore + locationScore + base);
    }

    private RecommendationResponse toRecommendation(
            CandidateProfile candidate,
            Set<String> candidateSkills,
            Job job,
            boolean lowConfidence,
            boolean saved,
            boolean applied,
            long applicationCount,
            int listingPriority
    ) {
        List<String> jobSkills = job.getSkills() == null ? List.of() : job.getSkills();
        List<String> matched = jobSkills.stream()
                .filter(skill -> candidateSkills.contains(skill.toLowerCase()))
                .toList();
        List<String> missing = jobSkills.stream()
                .filter(skill -> !candidateSkills.contains(skill.toLowerCase()))
                .limit(5)
                .toList();
        int score = calculateMatchScore(candidateSkills, candidate.getLocation(), job);
        String reason = matched.isEmpty()
                ? "Hoan thien ho so ky nang de nhan goi y chinh xac hon."
                : "Phu hop vi ban co " + String.join(", ", matched) + ".";
        JobResponse jobResponse = dtoMapper.toJobResponse(
                job, saved, applied, null, applicationCount, listingPriority
        );
        return new RecommendationResponse(jobResponse, score, matched, missing, reason, lowConfidence);
    }

    private UUID employerUserId(Job job) {
        return job != null && job.getEmployer() != null && job.getEmployer().getUser() != null
                ? job.getEmployer().getUser().getId()
                : null;
    }

    private Optional<CandidateProfile> currentCandidate() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        User user;
        if (authentication.getPrincipal() instanceof User entityUser) {
            user = entityUser;
        } else if (authentication.getPrincipal() instanceof UserResponse userResponse) {
            user = userRepository.findByEmail(userResponse.email()).orElse(null);
        } else {
            user = null;
        }
        if (user == null || user.getRoleEnum() != User.UserRole.CANDIDATE || !user.isEmailVerified() || user.getStatusEnum() != User.UserStatus.ACTIVE) {
            return Optional.empty();
        }
        return candidateProfileRepository.findWithSkillsByUserId(user.getId());
    }

    private Sort resolveSort(String sort) {
        if ("salary".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "salaryMax");
        }
        if ("deadline".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.ASC, "deadline");
        }
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    private Set<String> parseSkillFilter(String skills) {
        if (skills == null || skills.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(skills.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private Set<String> normalized(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toLowerCase())
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    private String buildRemoteJobWhereClause(String search, String location, BigDecimal minSalary, BigDecimal maxSalary,
                                             String experienceLevel, String skills, String category, String jobType,
                                             String workMode, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder("""
                WHERE j.status = 'published'
                  AND (j.deadline IS NULL OR j.deadline >= CURRENT_DATE)
                """);

        if (search != null && !search.isBlank()) {
            where.append("""
                  AND (
                    j.title ILIKE :search
                    OR j.description ILIKE :search
                    OR COALESCE(j.requirements, '') ILIKE :search
                    OR c.name ILIKE :search
                  )
                """);
            params.addValue("search", "%" + search.trim() + "%");
        }

        if (location != null && !location.isBlank()) {
            where.append("""
                  AND (
                    COALESCE(j.location, '') ILIKE :location
                    OR COALESCE(c.location, '') ILIKE :location
                  )
                """);
            params.addValue("location", "%" + location.trim() + "%");
        }

        if (minSalary != null) {
            where.append("  AND (j.salary_max IS NULL OR j.salary_max >= :minSalary)\n");
            params.addValue("minSalary", minSalary);
        }

        if (maxSalary != null) {
            where.append("  AND (j.salary_min IS NULL OR j.salary_min <= :maxSalary)\n");
            params.addValue("maxSalary", maxSalary);
        }

        if (experienceLevel != null && !experienceLevel.isBlank()) {
            where.append("  AND LOWER(COALESCE(j.experience_level, '')) = :experienceLevel\n");
            params.addValue("experienceLevel", experienceLevel.trim().toLowerCase(Locale.ROOT));
        }

        if (jobType != null && !jobType.isBlank()) {
            where.append("  AND LOWER(COALESCE(j.job_type, '')) = :jobType\n");
            params.addValue("jobType", jobType.trim().toLowerCase(Locale.ROOT));
        }

        if (workMode != null && !workMode.isBlank()) {
            where.append("  AND LOWER(COALESCE(j.work_mode, '')) = :workMode\n");
            params.addValue("workMode", workMode.trim().toLowerCase(Locale.ROOT));
        }

        if (category != null && !category.isBlank()) {
            where.append("""
                  AND (
                    EXISTS (
                      SELECT 1
                      FROM categories cat
                      WHERE cat.id = j.category_id
                        AND (
                          cat.id::text = :categoryRaw
                          OR LOWER(cat.slug) = :category
                          OR LOWER(cat.name) = :category
                        )
                    )
                    OR EXISTS (
                      SELECT 1
                      FROM job_skills jsc
                      JOIN skills sc ON sc.id = jsc.skill_id
                      WHERE jsc.job_id = j.id
                        AND (
                          LOWER(COALESCE(sc.category, '')) = :category
                          OR LOWER(sc.name) = :category
                        )
                    )
                    OR LOWER(COALESCE(c.industry, '')) = :category
                  )
                """);
            params.addValue("categoryRaw", category.trim());
            params.addValue("category", category.trim().toLowerCase(Locale.ROOT));
        }

        Set<String> skillFilters = parseSkillFilter(skills);
        if (!skillFilters.isEmpty()) {
            where.append("""
                  AND (
                    SELECT COUNT(DISTINCT LOWER(sf.name))
                    FROM job_skills jsf
                    JOIN skills sf ON sf.id = jsf.skill_id
                    WHERE jsf.job_id = j.id
                      AND LOWER(sf.name) IN (:skillFilters)
                  ) = :skillFilterCount
                """);
            params.addValue("skillFilters", skillFilters);
            params.addValue("skillFilterCount", skillFilters.size());
        }

        return where.toString();
    }

    /**
     * Sort mặc định: gói cao hơn lên trước → tin mới hơn → id (ổn định khi trùng thời gian).
     * Salary/deadline vẫn ưu tiên listingPriority trước.
     */
    static String resolveRemoteJobOrder(String sort) {
        String priority = FeatureLimitService.LISTING_PRIORITY_SQL + " DESC";
        String freshness = "COALESCE(j.published_at, j.posted_at, j.created_at) DESC";
        String tieBreak = "j.id DESC";
        if ("salary".equalsIgnoreCase(sort)) {
            return " ORDER BY " + priority + ", j.salary_max DESC NULLS LAST, " + freshness + ", " + tieBreak;
        }
        if ("deadline".equalsIgnoreCase(sort)) {
            return " ORDER BY " + priority + ", j.deadline ASC NULLS LAST, " + freshness + ", " + tieBreak;
        }
        return " ORDER BY " + priority + ", " + freshness + ", " + tieBreak;
    }

    private JobResponse mapRemoteJobResponse(ResultSet resultSet, int rowNumber) throws SQLException {
        return mapRemoteJobResponse(resultSet, rowNumber, null);
    }

    private JobResponse mapRemoteJobResponse(ResultSet resultSet, int rowNumber, CandidateProfile candidate) throws SQLException {
        String clId = resultSet.getString("company_location_id");
        CompanyLocationResponse clResp = clId == null ? null : new CompanyLocationResponse(
                clId,
                resultSet.getString("cl_branch_name"),
                resultSet.getString("cl_address"),
                resultSet.getString("cl_city"),
                resultSet.getString("cl_district"),
                resultSet.getString("cl_country"),
                resultSet.getBoolean("cl_is_headquarter")
        );
        int listingPriority = readListingPriority(resultSet);
        return new JobResponse(
                resultSet.getString("id"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                textToList(resultSet.getString("requirements")),
                textArrayToList(resultSet.getArray("skills")),
                resultSet.getBigDecimal("salary_min"),
                resultSet.getBigDecimal("salary_max"),
                resultSet.getString("location"),
                toFrontendExperienceLevel(resultSet.getString("experience_level")),
                readDeadline(resultSet),
                toFrontendStatus(resultSet.getString("status")),
                new CompanyResponse(
                        resultSet.getString("company_id"),
                        resultSet.getString("company_name"),
                        resultSet.getString("company_website"),
                        resultSet.getString("company_location"),
                        resultSet.getString("company_logo_url")
                ),
                clId,
                clResp,
                candidate != null && savedJobRepository.existsByCandidateIdAndJobId(candidate.getId(), java.util.UUID.fromString(resultSet.getString("id"))),
                candidate != null && applicationRepository.existsByCandidateIdAndJobId(candidate.getId(), java.util.UUID.fromString(resultSet.getString("id"))),
                null,
                resultSet.getString("benefits"),
                resultSet.getInt("vacancies"),
                resultSet.getString("working_time"),
                resultSet.getString("salary_type"),
                resultSet.getString("job_type"),
                resultSet.getString("work_mode"),
                resultSet.getInt("views_count"),
                resultSet.getString("rejection_reason"),
                0L,
                null,
                null,
                listingPriority,
                FeatureLimitService.isFeatured(listingPriority)
        );
    }

    private JobResponse withCandidateState(JobResponse job, CandidateProfile candidate, Set<UUID> savedIds, Set<UUID> appliedIds) {
        UUID jobId = UUID.fromString(job.id());
        return new JobResponse(
                job.id(), job.title(), job.description(), job.requirements(), job.skills(),
                job.salaryMin(), job.salaryMax(), job.location(), job.experienceLevel(), job.deadline(),
                job.status(), job.company(), job.companyLocationId(), job.companyLocation(),
                savedIds.contains(jobId), appliedIds.contains(jobId), null,
                job.benefits(), job.vacancies(), job.workingTime(), job.salaryType(), job.jobType(), job.workMode(),
                job.viewsCount(), job.rejectionReason(), job.applicationsCount(), job.reportFixDeadline(),
                job.rankingConfig(), job.listingPriority(), job.featured()
        );
    }

    private int readListingPriority(ResultSet resultSet) throws SQLException {
        try {
            return Math.max(0, resultSet.getInt("listing_priority"));
        } catch (SQLException ex) {
            return 0;
        }
    }

    private Job toJobForMatch(ResultSet resultSet) throws SQLException {
        Job job = new Job();
        job.setId(java.util.UUID.fromString(resultSet.getString("id")));
        job.setLocation(resultSet.getString("location"));
        job.setRequirements(textToList(resultSet.getString("requirements")));
        job.setSkills(textArrayToList(resultSet.getArray("skills")));
        return job;
    }

    private LocalDateTime readDeadline(ResultSet resultSet) throws SQLException {
        java.sql.Date deadline = resultSet.getDate("deadline");
        return deadline == null ? null : deadline.toLocalDate().atStartOfDay();
    }

    private List<String> textToList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private List<String> textArrayToList(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object rawArray = array.getArray();
        if (!(rawArray instanceof Object[] values)) {
            return List.of();
        }
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String toFrontendExperienceLevel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String toFrontendStatus(String status) {
        if (status == null) {
            return "DRAFT";
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "published" -> "ACTIVE";
            case "closed" -> "CLOSED";
            case "removed" -> "REMOVED";
            case "awaiting_company" -> "AWAITING_COMPANY";
            case "expired" -> "EXPIRED";
            default -> "DRAFT";
        };
    }

    private UUID parseUuid(String value, String code) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "Ma dinh danh khong hop le");
        }
    }
}
