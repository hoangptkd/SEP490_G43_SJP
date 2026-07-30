package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.CandidateProfileRequest;
import com.sjp.recruitment.model.dto.request.CvVersionRequest;
import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.model.entity.*;
import com.sjp.recruitment.repository.*;
import com.sjp.recruitment.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateService {

    private static final long MAX_CV_SIZE = 5L * 1024 * 1024;
    private static final String SOURCE_UPLOADED = "uploaded";
    private static final String SOURCE_BUILDER = "builder";

    private final AuthService authService;
    private final DtoMapper dtoMapper;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateCvRepository candidateCvRepository;
    private final CvVersionRepository cvVersionRepository;
    private final SavedJobRepository savedJobRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final NotificationRepository notificationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SkillRepository skillRepository;
    private final CandidateSkillRepository candidateSkillRepository;
    private final StorageService storageService;

    public record CvDownload(String fileName, String contentType, org.springframework.core.io.Resource resource) {
    }

    @Transactional(readOnly = true)
    public CandidateProfile getCurrentCandidateProfile() {
        User user = authService.getCurrentUser();
        requireCandidate(user);
        return candidateProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CANDIDATE_PROFILE_NOT_FOUND", "Chua co ho so ung vien"));
    }

    @Transactional(readOnly = true)
    public CandidateProfileResponse getProfile() {
        CandidateProfile profile = getCurrentCandidateProfile();
        return dtoMapper.toCandidateProfileResponse(profile, isApplyReady(profile));
    }

    @Transactional
    public CandidateProfileResponse updateProfile(CandidateProfileRequest request) {
        CandidateProfile profile = getCurrentCandidateProfile();
        profile.setFullName(request.fullName());
        profile.setPhone(request.phone());
        profile.setLocation(request.location());
        profile.setBio(request.bio());
        updateCandidateSkills(profile, request.skills() == null ? List.of() : request.skills());
        profile.setSkills(request.skills() == null ? List.of() : request.skills());
        profile.setEducation(request.education() == null ? List.of() : request.education());
        profile.setWorkExperience(request.workExperience() == null ? List.of() : request.workExperience());
        profile.setProjects(request.projects() == null ? List.of() : request.projects());
        profile.setCertifications(request.certifications() == null ? List.of() : request.certifications());
        return dtoMapper.toCandidateProfileResponse(candidateProfileRepository.save(profile), isApplyReady(profile));
    }

    @Transactional(readOnly = true)
    public boolean isApplyReady(CandidateProfile profile) {
        return hasText(profile.getFullName())
                && hasText(profile.getPhone())
                && hasText(profile.getLocation())
                && profile.getSkills() != null
                && !profile.getSkills().isEmpty()
                && candidateCvRepository.existsByCandidateIdAndSourceTypeAndDeletedAtIsNull(profile.getId(), SOURCE_UPLOADED);
    }

    @Transactional(readOnly = true)
    public List<CvResponse> getCvs() {
        CandidateProfile profile = getCurrentCandidateProfile();
        return candidateCvRepository.findByCandidateIdAndSourceTypeAndDeletedAtIsNullOrderByCreatedAtDesc(profile.getId(), SOURCE_UPLOADED)
                .stream()
                .map(dtoMapper::toCvResponse)
                .toList();
    }

    @Transactional
    public CvResponse uploadCv(MultipartFile file) {
        CandidateProfile profile = getCurrentCandidateProfile();
        validateCvFile(file);
        try {
            StorageService.StoredFile stored = storageService.storeCandidateCv(profile.getId(), file);
            CandidateCv cv = new CandidateCv();
            cv.setCandidate(profile);
            cv.setTitle(file.getOriginalFilename() == null ? "CV ung vien" : file.getOriginalFilename());
            cv.setOriginalFileName(file.getOriginalFilename() == null ? "cv.pdf" : file.getOriginalFilename());
            cv.setStorageKey(stored.storageKey());
            cv.setContentType(stored.contentType() == null ? "application/pdf" : stored.contentType());
            cv.setFileSize(stored.fileSize());
            cv.setSourceType(SOURCE_UPLOADED);
            boolean firstCv = !candidateCvRepository.existsByCandidateIdAndSourceTypeAndDeletedAtIsNull(profile.getId(), SOURCE_UPLOADED);
            cv.setDefaultCv(firstCv);
            return dtoMapper.toCvResponse(candidateCvRepository.save(cv));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CV_STORAGE_FAILED", "Khong the luu file CV");
        }
    }

    @Transactional
    public CvResponse setDefaultCv(String cvId) {
        CandidateProfile profile = getCurrentCandidateProfile();
        CandidateCv target = candidateCvRepository.findByIdAndCandidateIdAndSourceTypeAndDeletedAtIsNull(parseUuid(cvId, "CV_ID_INVALID"), profile.getId(), SOURCE_UPLOADED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CV_NOT_FOUND", "Khong tim thay CV"));
        candidateCvRepository.findByCandidateIdAndSourceTypeAndDeletedAtIsNullOrderByCreatedAtDesc(profile.getId(), SOURCE_UPLOADED)
                .forEach(cv -> cv.setDefaultCv(cv.getId().equals(target.getId())));
        return dtoMapper.toCvResponse(target);
    }

    @Transactional
    public void deleteCv(String cvId) {
        CandidateProfile profile = getCurrentCandidateProfile();
        CandidateCv cv = candidateCvRepository.findByIdAndCandidateIdAndSourceTypeAndDeletedAtIsNull(parseUuid(cvId, "CV_ID_INVALID"), profile.getId(), SOURCE_UPLOADED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CV_NOT_FOUND", "Khong tim thay CV"));
        if (applicationRepository.existsByCvId(cv.getId())) {
            cv.setDeletedAt(java.time.LocalDateTime.now());
            cv.setDefaultCv(false);
            ensureDefaultUploadedCv(profile.getId());
            return;
        }
        candidateCvRepository.delete(cv);
        ensureDefaultUploadedCv(profile.getId());
    }

    @Transactional(readOnly = true)
    public CvDownload downloadCv(String cvId) {
        CandidateProfile profile = getCurrentCandidateProfile();
        CandidateCv cv = candidateCvRepository.findByIdAndCandidateId(parseUuid(cvId, "CV_ID_INVALID"), profile.getId())
                .filter(this::isUploadedCv)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CV_NOT_FOUND", "Khong tim thay CV"));
        return toCvDownload(cv);
    }

    @Transactional(readOnly = true)
    public List<CvVersionResponse> getCvVersions() {
        CandidateProfile profile = getCurrentCandidateProfile();
        return cvVersionRepository.findByCandidateIdAndSourceTypeAndDeletedAtIsNullOrderByUpdatedAtDesc(profile.getId(), SOURCE_BUILDER)
                .stream()
                .map(dtoMapper::toCvVersionResponse)
                .toList();
    }

    @Transactional
    public CvVersionResponse createCvVersion(CvVersionRequest request) {
        CandidateProfile profile = getCurrentCandidateProfile();
        CvVersion version = new CvVersion();
        version.setCandidate(profile);
        version.setTitle(request.title());
        version.setSourceType(SOURCE_BUILDER);
        version.setTemplateKey(request.templateKey() == null || request.templateKey().isBlank() ? "classic" : request.templateKey());
        version.setSnapshot(request.snapshot() == null ? defaultSnapshot(profile) : request.snapshot());
        return dtoMapper.toCvVersionResponse(cvVersionRepository.save(version));
    }

    @Transactional
    public CvVersionResponse updateCvVersion(String id, CvVersionRequest request) {
        CandidateProfile profile = getCurrentCandidateProfile();
        CvVersion version = cvVersionRepository.findByIdAndCandidateIdAndSourceTypeAndDeletedAtIsNull(parseUuid(id, "CV_VERSION_ID_INVALID"), profile.getId(), SOURCE_BUILDER)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CV_VERSION_NOT_FOUND", "Khong tim thay ban CV"));
        version.setTitle(request.title());
        version.setTemplateKey(request.templateKey() == null || request.templateKey().isBlank() ? "classic" : request.templateKey());
        version.setSnapshot(request.snapshot() == null ? defaultSnapshot(profile) : request.snapshot());
        return dtoMapper.toCvVersionResponse(version);
    }

    @Transactional
    public void deleteCvVersion(String id) {
        CandidateProfile profile = getCurrentCandidateProfile();
        CvVersion version = cvVersionRepository.findByIdAndCandidateIdAndSourceTypeAndDeletedAtIsNull(parseUuid(id, "CV_VERSION_ID_INVALID"), profile.getId(), SOURCE_BUILDER)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CV_VERSION_NOT_FOUND", "Khong tim thay ban CV"));
        version.setDeletedAt(java.time.LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<JobResponse> getSavedJobs() {
        CandidateProfile profile = getCurrentCandidateProfile();
        return savedJobRepository.findByCandidateIdOrderByCreatedAtDesc(profile.getId())
                .stream()
                .map(saved -> dtoMapper.toJobResponse(saved.getJob(), true,
                        applicationRepository.existsByCandidateIdAndJobId(profile.getId(), saved.getJob().getId()), null))
                .toList();
    }

    @Transactional
    public void saveJob(String jobId) {
        CandidateProfile profile = getCurrentCandidateProfile();
        UUID parsedJobId = parseUuid(jobId, "JOB_ID_INVALID");
        if (savedJobRepository.existsByCandidateIdAndJobId(profile.getId(), parsedJobId)) {
            return;
        }
        Job job = jobRepository.findById(parsedJobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Khong tim thay viec lam"));
        SavedJob saved = new SavedJob();
        saved.setCandidate(profile);
        saved.setJob(job);
        savedJobRepository.save(saved);
    }

    @Transactional
    public void unsaveJob(String jobId) {
        CandidateProfile profile = getCurrentCandidateProfile();
        savedJobRepository.findByCandidateIdAndJobId(profile.getId(), parseUuid(jobId, "JOB_ID_INVALID"))
                .ifPresent(savedJobRepository::delete);
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
        Notification notification = notificationRepository.findByIdAndRecipientUserId(parseUuid(notificationId, "NOTIFICATION_ID_INVALID"), user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Khong tim thay thong bao"));
        notification.setRead(true);
    }

    @Transactional
    public void markAllNotificationsRead() {
        User user = authService.getCurrentUser();
        notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(user.getId())
                .forEach(notification -> notification.setRead(true));
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription() {
        User user = authService.getCurrentUser();
        Subscription subscription = subscriptionRepository.findTopByUserIdOrderByStartedAtDesc(user.getId()).orElse(null);
        Plan plan = subscription == null ? null : subscription.getPlan();
        return new SubscriptionResponse(
                plan == null ? "FREE" : plan.getCode(),
                plan == null ? "Free" : plan.getName(),
                subscription == null ? "ACTIVE" : subscription.getStatusEnum().name(),
                plan == null ? java.math.BigDecimal.ZERO : plan.getPrice(),
                plan == null ? List.of("Ho so ung vien", "Tim kiem viec lam", "Ung tuyen viec lam") : plan.getBenefits(),
                subscription == null ? null : subscription.getStartedAt(),
                subscription == null ? null : subscription.getExpiresAt(),
                getCurrentProfileIdIfCandidate(user) == null ? 0 : savedJobRepository.countByCandidateId(getCurrentProfileIdIfCandidate(user)),
                getCurrentProfileIdIfCandidate(user) == null ? 0 : candidateCvRepository.findByCandidateIdAndSourceTypeAndDeletedAtIsNullOrderByCreatedAtDesc(getCurrentProfileIdIfCandidate(user), SOURCE_UPLOADED).size(),
                notificationRepository.countByRecipientUserIdAndReadFalse(user.getId())
        );
    }

    private UUID getCurrentProfileIdIfCandidate(User user) {
        if (user.getRoleEnum() != User.UserRole.CANDIDATE) {
            return null;
        }
        return candidateProfileRepository.findByUserId(user.getId()).map(CandidateProfile::getId).orElse(null);
    }

    private void ensureDefaultUploadedCv(UUID candidateId) {
        List<CandidateCv> activeUploaded = candidateCvRepository
                .findByCandidateIdAndSourceTypeAndDeletedAtIsNullOrderByCreatedAtDesc(candidateId, SOURCE_UPLOADED);
        if (!activeUploaded.isEmpty() && activeUploaded.stream().noneMatch(CandidateCv::isDefaultCv)) {
            activeUploaded.get(0).setDefaultCv(true);
        }
    }

    public CvDownload toCvDownload(CandidateCv cv) {
        if (cv == null || !isUploadedCv(cv) || cv.getStorageKey() == null || cv.getStorageKey().isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CV_FILE_NOT_FOUND", "Khong tim thay file CV");
        }
        try {
            return new CvDownload(
                    cv.getOriginalFileName() == null || cv.getOriginalFileName().isBlank() ? "cv.pdf" : cv.getOriginalFileName(),
                    cv.getContentType() == null || cv.getContentType().isBlank() ? "application/pdf" : cv.getContentType(),
                    storageService.loadCandidateCv(cv.getStorageKey())
            );
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CV_FILE_NOT_FOUND", "Khong tim thay file CV");
        }
    }

    private boolean isUploadedCv(CandidateCv cv) {
        return cv != null && SOURCE_UPLOADED.equalsIgnoreCase(cv.getSourceType());
    }

    public void requireCandidate(User user) {
        if (user.getRoleEnum() != User.UserRole.CANDIDATE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CANDIDATE_REQUIRED", "Chi ung vien moi co the thuc hien thao tac nay");
        }
        if (!user.isEmailVerified() || user.getStatusEnum() != User.UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Email chua duoc xac minh");
        }
    }

    private void validateCvFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CV_FILE_REQUIRED", "Vui long chon file CV");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!name.endsWith(".pdf") || !contentType.contains("pdf")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CV_INVALID_TYPE", "Chi ho tro file CV dinh dang PDF");
        }
        if (file.getSize() > MAX_CV_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CV_FILE_TOO_LARGE", "File CV vuot qua dung luong 5MB");
        }
    }

    private Map<String, Object> defaultSnapshot(CandidateProfile profile) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("fullName", profile.getFullName());
        snapshot.put("phone", profile.getPhone());
        snapshot.put("location", profile.getLocation());
        snapshot.put("bio", profile.getBio());
        snapshot.put("skills", profile.getSkills());
        snapshot.put("education", profile.getEducation());
        snapshot.put("workExperience", profile.getWorkExperience());
        snapshot.put("projects", profile.getProjects());
        snapshot.put("certifications", profile.getCertifications());
        return snapshot;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void updateCandidateSkills(CandidateProfile profile, List<String> skillNames) {
        candidateSkillRepository.deleteByCandidateId(profile.getId());
        skillNames.stream()
                .map(String::trim)
                .filter(this::hasText)
                .distinct()
                .forEach(skillName -> {
                    Skill skill = skillRepository.findByNameIgnoreCase(skillName)
                            .orElseGet(() -> {
                                Skill created = new Skill();
                                created.setName(skillName);
                                created.setSlug(slugify(skillName));
                                created.setCategory("General");
                                return skillRepository.save(created);
                            });
                    CandidateSkill candidateSkill = new CandidateSkill();
                    candidateSkill.setCandidate(profile);
                    candidateSkill.setSkill(skill);
                    candidateSkill.setLevel("intermediate");
                    candidateSkillRepository.save(candidateSkill);
                });
    }

    private String slugify(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private UUID parseUuid(String value, String code) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "Ma dinh danh khong hop le");
        }
    }
}
