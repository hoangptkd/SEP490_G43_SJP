package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.ApplicationSubmitRequest;
import com.sjp.recruitment.model.dto.response.ApplicationResponse;
import com.sjp.recruitment.model.dto.response.ApplicationTimelineResponse;
import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.model.entity.ApplicationStatusHistory;
import com.sjp.recruitment.model.entity.CandidateCv;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.CvVersion;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.model.entity.Notification;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.ApplicationRepository;
import com.sjp.recruitment.repository.ApplicationStatusHistoryRepository;
import com.sjp.recruitment.repository.CandidateCvRepository;
import com.sjp.recruitment.repository.CandidateProfileRepository;
import com.sjp.recruitment.repository.CvVersionRepository;
import com.sjp.recruitment.repository.JobRepository;
import com.sjp.recruitment.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final CandidateProfileRepository candidateRepository;
    private final CandidateCvRepository candidateCvRepository;
    private final CvVersionRepository cvVersionRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final NotificationRepository notificationRepository;
    private final CandidateService candidateService;
    private final JobService jobService;
    private final DtoMapper dtoMapper;

    @Transactional(readOnly = true)
    public Page<Application> findByCandidateId(String candidateId, Pageable pageable) {
        return applicationRepository.findByCandidateId(parseUuid(candidateId, "CANDIDATE_ID_INVALID"), pageable);
    }

    @Transactional
    public Application apply(String candidateId, String jobId) {
        UUID parsedCandidateId = parseUuid(candidateId, "CANDIDATE_ID_INVALID");
        UUID parsedJobId = parseUuid(jobId, "JOB_ID_INVALID");
        Job job = jobRepository.findById(parsedJobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Khong tim thay viec lam"));

        CandidateProfile candidate = candidateRepository.findById(parsedCandidateId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CANDIDATE_NOT_FOUND", "Khong tim thay ung vien"));

        if (applicationRepository.existsByCandidateIdAndJobId(parsedCandidateId, parsedJobId)) {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_DUPLICATED", "Ban da ung tuyen viec lam nay");
        }

        Application application = new Application();
        application.setJob(job);
        application.setCandidate(candidate);
        application.setStatus(Application.ApplicationStatus.SUBMITTED);

        return applicationRepository.save(application);
    }

    @Transactional
    public ApplicationResponse submit(ApplicationSubmitRequest request) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        User user = candidate.getUser();
        candidateService.requireCandidate(user);
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

        CandidateCv cv = null;
        CvVersion cvVersion = null;
        if (request.cvId() != null) {
            cv = candidateCvRepository.findByIdAndCandidateId(parseUuid(request.cvId(), "CV_ID_INVALID"), candidate.getId())
                    .filter(candidateCv -> !candidateCv.isDeleted())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CV_NOT_FOUND", "Khong tim thay CV"));
        } else if (request.cvVersionId() != null) {
            cvVersion = cvVersionRepository.findByIdAndCandidateId(parseUuid(request.cvVersionId(), "CV_VERSION_ID_INVALID"), candidate.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CV_VERSION_NOT_FOUND", "Khong tim thay ban CV"));
        } else {
            cv = candidateCvRepository.findByCandidateIdOrderByCreatedAtDesc(candidate.getId()).stream()
                    .filter(CandidateCv::isDefaultCv)
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CV_REQUIRED", "Vui long chon CV de ung tuyen"));
        }

        Application application = new Application();
        application.setCandidate(candidate);
        application.setJob(job);
        application.setCv(cv);
        application.setCvVersion(cvVersion);
        application.setStatus(Application.ApplicationStatus.SUBMITTED);
        Application saved = applicationRepository.save(application);

        addHistory(saved, null, Application.ApplicationStatus.SUBMITTED, "Ho so ung tuyen da duoc gui thanh cong.");
        createNotification(user, "APPLICATION_SUBMITTED", "Da gui ho so ung tuyen",
                "Ban da ung tuyen thanh cong vao vi tri " + job.getTitle() + ".", saved.getId());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> myApplications() {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        return applicationRepository.findByCandidateId(candidate.getId(), Pageable.unpaged())
                .getContent()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse myApplication(String id) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        Application application = applicationRepository.findById(parseUuid(id, "APPLICATION_ID_INVALID"))
                .filter(item -> item.getCandidate().getId().equals(candidate.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Khong tim thay ho so ung tuyen"));
        return toResponse(application);
    }

    @Transactional
    public void seedStatus(Application application, Application.ApplicationStatus toStatus, String note) {
        Application.ApplicationStatus from = application.getStatusEnum();
        application.setStatus(toStatus);
        addHistory(application, from, toStatus, note);
        createNotification(application.getCandidate().getUser(), "APPLICATION_STATUS_CHANGED",
                "Trang thai ung tuyen da cap nhat", note, application.getId());
    }

    private ApplicationResponse toResponse(Application application) {
        List<ApplicationTimelineResponse> timeline = historyRepository.findByApplicationIdOrderByCreatedAtAsc(application.getId())
                .stream()
                .map(dtoMapper::toTimelineResponse)
                .toList();
        return dtoMapper.toApplicationResponse(
                application,
                jobService.toJobResponse(application.getJob(), application.getCandidate()),
                timeline);
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

    private UUID parseUuid(String value, String code) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "Ma dinh danh khong hop le");
        }
    }
}
