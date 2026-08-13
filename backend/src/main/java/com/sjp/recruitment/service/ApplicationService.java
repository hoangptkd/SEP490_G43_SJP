package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.ApplicationSubmitRequest;
import com.sjp.recruitment.model.dto.response.ApplicationResponse;
import com.sjp.recruitment.model.dto.response.ApplicationTimelineResponse;
import com.sjp.recruitment.model.dto.response.PageResponse;
import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.model.entity.ApplicationStatusHistory;
import com.sjp.recruitment.model.entity.CandidateCv;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.CvVersion;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.model.dto.JobSnapshot;
import com.sjp.recruitment.model.dto.SubmittedResumeSnapshot;
import com.sjp.recruitment.model.entity.Notification;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.ApplicationRepository;
import com.sjp.recruitment.repository.ApplicationStatusHistoryRepository;
import com.sjp.recruitment.repository.CandidateCvRepository;
import com.sjp.recruitment.repository.CvVersionRepository;
import com.sjp.recruitment.repository.JobRepository;
import com.sjp.recruitment.repository.NotificationRepository;
import com.sjp.recruitment.repository.InterviewScheduleRepository;
import com.sjp.recruitment.repository.JobOfferRepository;
import com.sjp.recruitment.repository.EmployerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

    private static final String SOURCE_UPLOADED = "uploaded";
    private static final String SOURCE_BUILDER = "builder";
    private static final int COVER_LETTER_MAX_LENGTH = 2000;

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final CandidateCvRepository candidateCvRepository;
    private final CvVersionRepository cvVersionRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final NotificationRepository notificationRepository;
    private final InterviewScheduleRepository interviewScheduleRepository;
    private final JobOfferRepository jobOfferRepository;
    private final CandidateService candidateService;
    private final JobService jobService;
    private final DtoMapper dtoMapper;
    private final FeatureLimitService featureLimitService;
    private final EmployerRepository employerRepository;
    private final AiRankingService aiRankingService;

    @Transactional(readOnly = true)
    public Page<Application> findByCandidateId(String candidateId, Pageable pageable) {
        return applicationRepository.findByCandidateId(parseUuid(candidateId, "CANDIDATE_ID_INVALID"), pageable);
    }

    @Transactional
    public ApplicationResponse submit(ApplicationSubmitRequest request) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        User user = candidate.getUser();
        candidateService.requireCandidate(user);
        featureLimitService.requireApplication(user);
        if (!candidateService.isApplyReady(candidate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROFILE_INCOMPLETE", "Can hoan thien ho so va co it nhat 1 CV truoc khi ung tuyen");
        }

        UUID jobId = parseUuid(request.jobId(), "JOB_ID_INVALID");
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Khong tim thay viec lam"));
        if (!"published".equals(job.getStatus()) || (job.getDeadline() != null && job.getDeadline().isBefore(LocalDate.now()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "JOB_NOT_OPEN", "Viec lam da dong hoac het han");
        }
        if (applicationRepository.existsByCandidateIdAndJobId(candidate.getId(), job.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_DUPLICATED", "Ban da ung tuyen viec lam nay");
        }

        boolean hasUploadedCv = hasText(request.cvId());
        boolean hasBuilderCv = hasText(request.cvVersionId());
        if (hasUploadedCv == hasBuilderCv) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CV_REFERENCE_INVALID", "Vui long chon chinh xac mot CV de ung tuyen");
        }

        CandidateCv cv;
        CvVersion cvVersion = null;
        if (hasUploadedCv) {
            cv = candidateCvRepository.findByIdAndCandidateIdAndSourceTypeAndDeletedAtIsNull(parseUuid(request.cvId().trim(), "CV_ID_INVALID"), candidate.getId(), SOURCE_UPLOADED)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CV_NOT_FOUND", "Khong tim thay CV"));
        } else {
            UUID cvVersionId = parseUuid(request.cvVersionId().trim(), "CV_VERSION_ID_INVALID");
            cvVersion = cvVersionRepository.findByIdAndCandidateIdAndSourceTypeAndDeletedAtIsNull(cvVersionId, candidate.getId(), SOURCE_BUILDER)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CV_VERSION_NOT_FOUND", "Khong tim thay ban CV"));
            cv = candidateCvRepository.findByIdAndCandidateId(cvVersion.getId(), candidate.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CV_VERSION_NOT_FOUND", "Khong tim thay ban CV"));
        }

        Application application = new Application();
        application.setCandidate(candidate);
        application.setJob(job);
        application.setCv(cv);
        application.setCvVersion(cvVersion);
        application.setResumeSnapshot(hasUploadedCv
                ? SubmittedResumeSnapshot.fromUploaded(cv)
                : SubmittedResumeSnapshot.fromBuilder(cvVersion));
        application.setResumeFileStorageKeySnapshot(hasUploadedCv ? cv.getStorageKey() : null);
        application.setPreferredLocation(trimToNull(request.preferredLocation()));
        application.setCoverLetter(cleanCoverLetter(request.coverLetter()));
        application.setStatus(Application.ApplicationStatus.SUBMITTED);
        application.setJobSnapshotJson(JobSnapshot.fromJob(job));
        Application saved = applicationRepository.save(application);

        addHistory(saved, null, Application.ApplicationStatus.SUBMITTED, "Ho so ung tuyen da duoc gui thanh cong.");
        createNotification(user, "APPLICATION_SUBMITTED", "Da gui ho so ung tuyen",
                "Ban da ung tuyen thanh cong vao vi tri " + job.getTitle() + ".", saved.getId());
        featureLimitService.consumeApplication(user);

        if (job.getCompany() != null) {
            employerRepository.findByCompanyIdWithUser(job.getCompany().getId())
                    .forEach(employer -> {
                        if (employer.getUser() != null) {
                            createNotification(employer.getUser(), "APPLICATION_RECEIVED", "Có ứng viên mới",
                                    "Ứng viên " + user.getFullName() + " vừa nộp hồ sơ vào vị trí " + job.getTitle() + ".", saved.getId());
                        }
                    });
        }

        // Auto ranking has been disabled per user request

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> myApplications(int page, int size) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        Page<Application> result = applicationRepository.findByCandidateId(
                candidate.getId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "submittedAt"))
        );
        return PageResponse.from(result, this::toResponse);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse myApplication(String id) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        Application application = applicationRepository.findById(parseUuid(id, "APPLICATION_ID_INVALID"))
                .filter(item -> item.getCandidate().getId().equals(candidate.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Khong tim thay ho so ung tuyen"));
        return toResponse(application);
    }

    @Transactional(readOnly = true)
    public CandidateService.CvDownload downloadMySubmittedCv(String id) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        Application application = applicationRepository.findById(parseUuid(id, "APPLICATION_ID_INVALID"))
                .filter(item -> item.getCandidate().getId().equals(candidate.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Khong tim thay ho so ung tuyen"));
        return toSubmittedCvDownload(application);
    }

    public CandidateService.CvDownload toSubmittedCvDownload(Application application) {
        SubmittedResumeSnapshot snapshot = application.getResumeSnapshot();
        if (snapshot != null && "builder".equalsIgnoreCase(snapshot.sourceType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CV_DOWNLOAD_NOT_AVAILABLE", "CV Builder duoc hien thi truc tiep, khong co file de tai");
        }
        String storageKey = application.getResumeFileStorageKeySnapshot();
        String fileName = snapshot != null ? snapshot.originalFileName() : null;
        String contentType = snapshot != null ? snapshot.contentType() : null;
        if ((storageKey == null || storageKey.isBlank()) && application.getCv() != null) {
            storageKey = application.getCv().getStorageKey();
            fileName = application.getCv().getOriginalFileName();
            contentType = application.getCv().getContentType();
        }
        return candidateService.toCvDownload(storageKey, fileName, contentType);
    }

    @Transactional
    public void seedStatus(Application application, Application.ApplicationStatus toStatus, String note) {
        Application.ApplicationStatus from = application.getStatusEnum();
        application.setStatus(toStatus);
        applicationRepository.save(application);
        addHistory(application, from, toStatus, note);
        createNotification(application.getCandidate().getUser(), "APPLICATION_STATUS_CHANGED",
                "Trang thai ung tuyen da cap nhat", note, application.getId());
        if (toStatus == Application.ApplicationStatus.HIRED && application.getJob() != null) {
            Job job = application.getJob();
            long hiredCount = applicationRepository.countByJobIdAndStatus(job.getId(), "hired");
            if (job.getVacancies() != null && hiredCount >= job.getVacancies() && !"closed".equalsIgnoreCase(job.getStatus())) {
                job.setStatus("closed");
                job.setClosedAt(LocalDateTime.now());
                jobRepository.save(job);
                jobService.notifyCandidatesJobClosed(job, "Tin tuyển dụng [" + job.getTitle() + "] mà bạn nộp đơn ứng tuyển đã tuyển đủ số lượng chỉ tiêu và tự động đóng.");
            }
        }
    }

    @Transactional
    public ApplicationResponse updateStatus(UUID applicationId, Application.ApplicationStatus toStatus, String note) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Khong tim thay ho so ung tuyen"));
        seedStatus(application, toStatus, note != null ? note : "Cap nhat trang thai ung tuyen");
        return toResponse(application);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse toResponse(Application application) {
        if (application == null) {
            return null;
        }
        List<ApplicationTimelineResponse> timeline = historyRepository.findByApplicationIdOrderByCreatedAtAsc(application.getId())
                .stream()
                .map(dtoMapper::toTimelineResponse)
                .toList();

        List<com.sjp.recruitment.model.dto.response.InterviewScheduleResponse> interviews = interviewScheduleRepository.findByApplicationId(application.getId())
                .stream()
                .map(dtoMapper::toInterviewScheduleResponse)
                .toList();

        com.sjp.recruitment.model.dto.response.JobOfferResponse jobOffer = jobOfferRepository.findByApplicationId(application.getId())
                .map(dtoMapper::toJobOfferResponse)
                .orElse(null);

        com.sjp.recruitment.model.dto.response.JobResponse jobResponse = application.getJob() != null
                ? jobService.toJobResponse(application.getJob(), application.getCandidate())
                : null;

        if (jobResponse != null && application.getJobSnapshotJson() != null) {
            JobSnapshot snap = application.getJobSnapshotJson();
            jobResponse = new com.sjp.recruitment.model.dto.response.JobResponse(
                    jobResponse.id(),
                    snap.getTitle() != null ? snap.getTitle() : jobResponse.title(),
                    snap.getDescription() != null ? snap.getDescription() : jobResponse.description(),
                    snap.getRequirements() != null ? snap.getRequirements() : jobResponse.requirements(),
                    jobResponse.skills(),
                    snap.getSalaryMin() != null ? snap.getSalaryMin() : jobResponse.salaryMin(),
                    snap.getSalaryMax() != null ? snap.getSalaryMax() : jobResponse.salaryMax(),
                    snap.getLocation() != null ? snap.getLocation() : jobResponse.location(),
                    snap.getExperienceLevel() != null ? snap.getExperienceLevel() : jobResponse.experienceLevel(),
                    snap.getDeadline() != null ? snap.getDeadline().atStartOfDay() : jobResponse.deadline(),
                    jobResponse.status(),
                    jobResponse.company(),
                    jobResponse.companyLocationId(),
                    jobResponse.companyLocation(),
                    jobResponse.saved(),
                    jobResponse.applied(),
                    jobResponse.matchScore(),
                    snap.getBenefits() != null ? snap.getBenefits() : jobResponse.benefits(),
                    snap.getVacancies() != null ? snap.getVacancies() : jobResponse.vacancies(),
                    snap.getWorkingTime() != null ? snap.getWorkingTime() : jobResponse.workingTime(),
                    snap.getSalaryType() != null ? snap.getSalaryType() : jobResponse.salaryType(),
                    snap.getJobType() != null ? snap.getJobType() : jobResponse.jobType(),
                    snap.getWorkMode() != null ? snap.getWorkMode() : jobResponse.workMode(),
                    jobResponse.viewsCount(),
                    jobResponse.rejectionReason(),
                    jobResponse.applicationsCount(),
                    jobResponse.reportFixDeadline(),
                    jobResponse.rankingConfig(),
                    jobResponse.listingPriority(),
                    jobResponse.featured()
            );
        }

        return dtoMapper.toApplicationResponse(
                application,
                jobResponse,
                timeline,
                interviews,
                jobOffer);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> toResponseBulk(List<Application> applications) {
        if (applications == null || applications.isEmpty()) return List.of();

        List<UUID> appIds = applications.stream().map(Application::getId).toList();

        // 1. Fetch related entities in bulk
        java.util.Map<UUID, List<ApplicationStatusHistory>> historyMap = historyRepository.findByApplicationIdInOrderByCreatedAtAsc(appIds)
                .stream().collect(java.util.stream.Collectors.groupingBy(h -> h.getApplication().getId()));

        java.util.Map<UUID, List<com.sjp.recruitment.model.entity.InterviewSchedule>> interviewMap = interviewScheduleRepository.findByApplicationIdIn(appIds)
                .stream().collect(java.util.stream.Collectors.groupingBy(i -> i.getApplication().getId()));

        java.util.Map<UUID, List<com.sjp.recruitment.model.entity.JobOffer>> offerMap = jobOfferRepository.findByApplicationIdIn(appIds)
                .stream().collect(java.util.stream.Collectors.groupingBy(o -> o.getApplication().getId()));

        // 2. We can also bulk map Jobs, but for now we rely on the existing jobService.toJobResponse caching/batching 
        // if JobService is not batched yet, this will still be N+1 for Job. 
        // Wait, EmployerService.getCompanyApplications usually filters by 1 Job, so Job is already loaded.

        return applications.stream().map(application -> {
            List<ApplicationTimelineResponse> timeline = historyMap.getOrDefault(application.getId(), List.of())
                    .stream()
                    .map(dtoMapper::toTimelineResponse)
                    .toList();

            List<com.sjp.recruitment.model.dto.response.InterviewScheduleResponse> interviews = interviewMap.getOrDefault(application.getId(), List.of())
                    .stream()
                    .map(dtoMapper::toInterviewScheduleResponse)
                    .toList();

            com.sjp.recruitment.model.dto.response.JobOfferResponse jobOffer = offerMap.getOrDefault(application.getId(), List.of())
                    .stream().findFirst()
                    .map(dtoMapper::toJobOfferResponse)
                    .orElse(null);

            com.sjp.recruitment.model.dto.response.JobResponse jobResponse = application.getJob() != null
                    ? jobService.toJobResponse(application.getJob(), application.getCandidate())
                    : null;

            if (jobResponse != null && application.getJobSnapshotJson() != null) {
                JobSnapshot snap = application.getJobSnapshotJson();
                jobResponse = new com.sjp.recruitment.model.dto.response.JobResponse(
                        jobResponse.id(),
                        snap.getTitle() != null ? snap.getTitle() : jobResponse.title(),
                        snap.getDescription() != null ? snap.getDescription() : jobResponse.description(),
                        snap.getRequirements() != null ? snap.getRequirements() : jobResponse.requirements(),
                        jobResponse.skills(),
                        snap.getSalaryMin() != null ? snap.getSalaryMin() : jobResponse.salaryMin(),
                        snap.getSalaryMax() != null ? snap.getSalaryMax() : jobResponse.salaryMax(),
                        snap.getLocation() != null ? snap.getLocation() : jobResponse.location(),
                        snap.getExperienceLevel() != null ? snap.getExperienceLevel() : jobResponse.experienceLevel(),
                        snap.getDeadline() != null ? snap.getDeadline().atStartOfDay() : jobResponse.deadline(),
                        jobResponse.status(),
                        jobResponse.company(),
                        jobResponse.companyLocationId(),
                        jobResponse.companyLocation(),
                        jobResponse.saved(),
                        jobResponse.applied(),
                        jobResponse.matchScore(),
                        snap.getBenefits() != null ? snap.getBenefits() : jobResponse.benefits(),
                        snap.getVacancies() != null ? snap.getVacancies() : jobResponse.vacancies(),
                        snap.getWorkingTime() != null ? snap.getWorkingTime() : jobResponse.workingTime(),
                        snap.getSalaryType() != null ? snap.getSalaryType() : jobResponse.salaryType(),
                        snap.getJobType() != null ? snap.getJobType() : jobResponse.jobType(),
                        snap.getWorkMode() != null ? snap.getWorkMode() : jobResponse.workMode(),
                        jobResponse.viewsCount(),
                        jobResponse.rejectionReason(),
                        jobResponse.applicationsCount(),
                        jobResponse.reportFixDeadline(),
                        jobResponse.rankingConfig(),
                        jobResponse.listingPriority(),
                        jobResponse.featured()
                );
            }

            return dtoMapper.toApplicationResponse(
                    application,
                    jobResponse,
                    timeline,
                    interviews,
                    jobOffer);
        }).toList();
    }


    private void addHistory(Application application, Application.ApplicationStatus from, Application.ApplicationStatus to, String note) {
        ApplicationStatusHistory history = new ApplicationStatusHistory();
        history.setApplication(application);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setPublicNote(note);
        historyRepository.save(history);
    }

    private void createNotification(User user, String type, String title, String message, UUID applicationId) {
        Notification notification = new Notification();
        notification.setRecipientUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRelatedEntityType("APPLICATION");
        notification.setRelatedEntityId(applicationId);
        notificationRepository.save(notification);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String cleanCoverLetter(String value) {
        String trimmed = trimToNull(value);
        if (trimmed != null && trimmed.length() > COVER_LETTER_MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COVER_LETTER_TOO_LONG", "Thu gioi thieu khong duoc vuot qua 2000 ky tu");
        }
        return trimmed;
    }

    private UUID parseUuid(String value, String code) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "Ma dinh danh khong hop le");
        }
    }
}
