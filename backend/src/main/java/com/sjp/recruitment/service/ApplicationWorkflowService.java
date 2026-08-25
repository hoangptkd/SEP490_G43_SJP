package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.InterviewCandidateResponseRequest;
import com.sjp.recruitment.model.dto.request.InterviewResultRequest;
import com.sjp.recruitment.model.dto.request.InterviewScheduleRequest;
import com.sjp.recruitment.model.dto.request.JobOfferRequest;
import com.sjp.recruitment.model.dto.request.CandidateOfferResponseRequest;
import com.sjp.recruitment.model.dto.response.InterviewScheduleResponse;
import com.sjp.recruitment.model.dto.response.JobOfferResponse;
import com.sjp.recruitment.model.entity.*;
import com.sjp.recruitment.repository.ApplicationRepository;
import com.sjp.recruitment.repository.InterviewScheduleRepository;
import com.sjp.recruitment.repository.JobOfferRepository;
import com.sjp.recruitment.repository.EmployerRepository;
import com.sjp.recruitment.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationWorkflowService {

    private final ApplicationRepository applicationRepository;
    private final InterviewScheduleRepository interviewScheduleRepository;
    private final JobOfferRepository jobOfferRepository;
    private final EmailService emailService;
    private final DtoMapper dtoMapper;
    private final ApplicationService applicationService;
    private final EmployerRepository employerRepository;
    private final NotificationRepository notificationRepository;

    private void createNotification(User user, String type, String title, String message, UUID entityId, String entityType) {
        Notification notification = new Notification();
        notification.setRecipientUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRelatedEntityType(entityType);
        notification.setRelatedEntityId(entityId);
        notificationRepository.save(notification);
    }

    @Transactional
    public void rejectApplication(UUID applicationId, UUID employerId, String note) {
        Application application = getApplicationAndVerifyEmployer(applicationId, employerId);
        applicationService.seedStatus(application, Application.ApplicationStatus.REJECTED, note);

        // Send email
        CandidateProfile candidate = application.getCandidate();
        Job job = application.getJob();
        Company company = job.getCompany();

        emailService.sendApplicationRejectionEmail(
                candidate.getUser().getEmail(),
                candidate.getFullName(),
                job.getTitle(),
                company.getName()
        );
    }

    @Transactional
    public InterviewScheduleResponse scheduleInterview(UUID applicationId, UUID employerId, InterviewScheduleRequest request) {
        Application application = getApplicationAndVerifyEmployer(applicationId, employerId);

        validateNoInterviewTimeConflict(application.getJob().getId(), request.scheduledAt(), null);

        List<InterviewSchedule> existingSchedules = interviewScheduleRepository.findByApplicationId(applicationId);
        int roundNumber = existingSchedules.size() + 1;

        InterviewSchedule schedule = new InterviewSchedule();
        schedule.setApplication(application);
        schedule.setEmployer(application.getJob().getEmployer()); // or the one requesting
        schedule.setCandidate(application.getCandidate());
        schedule.setRoundNumber(roundNumber);
        schedule.setScheduledAt(request.scheduledAt());
        schedule.setMeetingLink(request.meetingLink());
        schedule.setLocation(request.location());
        schedule.setNote(request.note());
        schedule.setStatus("PENDING_RESPONSE");
        schedule.setResponseDeadline(calculateResponseDeadline(request.scheduledAt()));
        schedule.setReminderCount(0);

        InterviewSchedule saved = interviewScheduleRepository.save(schedule);

        applicationService.seedStatus(application, Application.ApplicationStatus.INTERVIEW_SCHEDULED, "Đã lên lịch phỏng vấn");

        // Send email
        CandidateProfile candidate = application.getCandidate();
        Job job = application.getJob();
        Company company = job.getCompany();

        emailService.sendInterviewInvitationEmail(
                candidate.getUser().getEmail(),
                candidate.getFullName(),
                job.getTitle(),
                company.getName(),
                request.scheduledAt().toString(), // format better later
                request.location(),
                request.meetingLink(),
                request.note()
        );

        return dtoMapper.toInterviewScheduleResponse(saved);
    }

    private LocalDateTime calculateResponseDeadline(LocalDateTime scheduledAt) {
        if (scheduledAt == null) return null;
        LocalDateTime now = LocalDateTime.now();
        long hoursDiff = java.time.Duration.between(now, scheduledAt).toHours();
        if (hoursDiff > 24) {
            return scheduledAt.minusHours(12);
        } else if (hoursDiff >= 12) {
            return scheduledAt.minusHours(6);
        } else {
            return scheduledAt;
        }
    }

    @Transactional
    public InterviewScheduleResponse candidateViewInterview(UUID scheduleId, UUID candidateId) {
        InterviewSchedule schedule = interviewScheduleRepository.findByIdAndCandidateId(scheduleId, candidateId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Không tìm thấy lịch phỏng vấn"));

        if (schedule.getViewedAt() == null) {
            schedule.setViewedAt(LocalDateTime.now());
            schedule = interviewScheduleRepository.save(schedule);
        }

        return dtoMapper.toInterviewScheduleResponse(schedule);
    }

    @Transactional
    public InterviewScheduleResponse candidateRespondToInterview(UUID scheduleId, UUID candidateId, InterviewCandidateResponseRequest request) {
        InterviewSchedule schedule = interviewScheduleRepository.findByIdAndCandidateId(scheduleId, candidateId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Không tìm thấy lịch phỏng vấn"));

        if ("NO_RESPONSE".equalsIgnoreCase(schedule.getStatus())
                || (schedule.getResponseDeadline() != null && LocalDateTime.now().isAfter(schedule.getResponseDeadline()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INTERVIEW_EXPIRED", "Lịch phỏng vấn đã hết hạn phản hồi");
        }

        schedule.setRespondedAt(LocalDateTime.now());
        schedule.setCandidateRescheduleNote(request.rescheduleNote());

        if ("confirmed".equals(request.response())) {
            schedule.setStatus("ACCEPTED");
            schedule.setCandidateRescheduleNote(null);
        } else if ("request_reschedule".equals(request.response())) {
            schedule.setStatus("RESCHEDULE_REQUESTED");
        } else if ("declined".equals(request.response())) {
            schedule.setStatus("DECLINED");
        }

        InterviewSchedule saved = interviewScheduleRepository.save(schedule);

        // Notify employer
        String responseText = switch (request.response()) {
            case "confirmed" -> "xác nhận tham gia phỏng vấn";
            case "declined" -> "từ chối tham gia phỏng vấn";
            default -> "yêu cầu đổi lịch phỏng vấn";
        };
        employerRepository.findByCompanyId(schedule.getApplication().getJob().getCompany().getId()).forEach(employer -> {
            if (employer.getUser() != null) {
                createNotification(employer.getUser(), "CANDIDATE_RESPONDED_INTERVIEW", "Ứng viên phản hồi lịch phỏng vấn",
                        "Ứng viên " + schedule.getCandidate().getFullName() + " đã " + responseText + " cho vị trí " + schedule.getApplication().getJob().getTitle(), schedule.getApplication().getId(), "APPLICATION");
            }
        });

        if ("declined".equals(request.response())) {
            applicationService.seedStatus(schedule.getApplication(), Application.ApplicationStatus.REJECTED, "Ứng viên đã từ chối tham gia phỏng vấn");
        } else if ("request_reschedule".equals(request.response())) {
            String noteStr = "Ứng viên đã yêu cầu đổi lịch phỏng vấn";
            if (request.rescheduleNote() != null && !request.rescheduleNote().isBlank()) {
                noteStr += " (Lý do: " + request.rescheduleNote().trim() + ")";
            }
            applicationService.seedStatus(schedule.getApplication(), schedule.getApplication().getStatusEnum(), noteStr);
        } else {
            applicationService.seedStatus(schedule.getApplication(), schedule.getApplication().getStatusEnum(), "Ứng viên đã " + responseText);
        }

        return dtoMapper.toInterviewScheduleResponse(saved);
    }

    @Transactional
    public InterviewScheduleResponse employerUpdateInterviewResult(UUID scheduleId, UUID employerId, InterviewResultRequest request) {
        InterviewSchedule schedule = interviewScheduleRepository.findByIdAndEmployerId(scheduleId, employerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Không tìm thấy lịch phỏng vấn"));

        if (!"ACCEPTED".equalsIgnoreCase(schedule.getStatus()) && !"COMPLETED".equalsIgnoreCase(schedule.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INTERVIEW_NOT_ACCEPTED", "Ứng viên chưa xác nhận tham gia phỏng vấn");
        }

        if (schedule.getScheduledAt() != null && schedule.getScheduledAt().isAfter(LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INTERVIEW_NOT_STARTED_YET", "Chưa đến thời gian phỏng vấn. Bạn chỉ có thể đánh giá kết quả sau khi thời gian phỏng vấn bắt đầu.");
        }

        schedule.setStatus("COMPLETED".equalsIgnoreCase(request.result()) ? "COMPLETED" : "NO_SHOW".equalsIgnoreCase(request.result()) ? "NO_SHOW" : request.result());
        schedule.setNote(request.note());

        InterviewSchedule saved = interviewScheduleRepository.save(schedule);

        if ("NO_SHOW".equalsIgnoreCase(request.result()) || "fail".equalsIgnoreCase(request.result())) {
            Application application = schedule.getApplication();
            applicationService.seedStatus(application, Application.ApplicationStatus.REJECTED, "Không đạt yêu cầu phỏng vấn");

            CandidateProfile candidate = application.getCandidate();
            Job job = application.getJob();
            Company company = job.getCompany();

            emailService.sendInterviewResultFailedEmail(
                    candidate.getUser().getEmail(),
                    candidate.getFullName(),
                    job.getTitle(),
                    company.getName()
            );
        } else if ("COMPLETED".equalsIgnoreCase(request.result())) {
            Application application = schedule.getApplication();
            
            applicationService.seedStatus(application, application.getStatusEnum(), "Đánh giá phỏng vấn: Đạt");
        }

        return dtoMapper.toInterviewScheduleResponse(saved);
    }

    @Transactional
    public InterviewScheduleResponse employerRespondToReschedule(UUID scheduleId, UUID employerId, com.sjp.recruitment.model.dto.request.EmployerRescheduleResponseRequest request) {
        InterviewSchedule schedule = interviewScheduleRepository.findByIdAndEmployerId(scheduleId, employerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Không tìm thấy lịch phỏng vấn"));

        if (!"RESCHEDULE_REQUESTED".equals(schedule.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Ứng viên chưa yêu cầu đổi lịch");
        }

        schedule.setEmployerRescheduleResponse(request.response());
        schedule.setEmployerRescheduleNote(request.note());

        String oldTimeStr = schedule.getScheduledAt() != null ? schedule.getScheduledAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")) : "Chưa có";
        if ("accept_reschedule".equals(request.response())) {
            if (request.scheduledAt() != null) {
                validateNoInterviewTimeConflict(schedule.getApplication().getJob().getId(), request.scheduledAt(), schedule.getId());
                schedule.setScheduledAt(request.scheduledAt());
                
                schedule.setResponseDeadline(calculateResponseDeadline(request.scheduledAt()));
            }
        }
        
        schedule.setRespondedAt(null);
        schedule.setViewedAt(null);
        schedule.setLastReminderAt(null);
        schedule.setReminderCount(0);
        schedule.setStatus("PENDING_RESPONSE");

        InterviewSchedule saved = interviewScheduleRepository.save(schedule);

        // Send email
        CandidateProfile candidate = schedule.getApplication().getCandidate();
        Job job = schedule.getApplication().getJob();
        Company company = job.getCompany();

        if ("accept_reschedule".equals(request.response())) {
            emailService.sendInterviewRescheduledEmail(
                    candidate.getUser().getEmail(),
                    candidate.getFullName(),
                    job.getTitle(),
                    company.getName(),
                    saved.getScheduledAt().toString(),
                    saved.getLocation(),
                    saved.getMeetingLink(),
                    request.note()
            );
        } else {
            emailService.sendInterviewRescheduleRejectedEmail(
                    candidate.getUser().getEmail(),
                    candidate.getFullName(),
                    job.getTitle(),
                    company.getName(),
                    saved.getScheduledAt().toString(),
                    saved.getLocation(),
                    saved.getMeetingLink(),
                    request.note()
            );
        }
        
        String seedNote;
        if ("accept_reschedule".equals(request.response())) {
            if (request.scheduledAt() != null) {
                String newTimeStr = request.scheduledAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
                seedNote = "Đã đổi lịch phỏng vấn từ " + oldTimeStr + " thành " + newTimeStr;
            } else {
                seedNote = "Nhà tuyển dụng đã chấp nhận đổi lịch phỏng vấn";
            }
        } else {
            seedNote = "Nhà tuyển dụng phản hồi đổi lịch: Từ chối đổi lịch phỏng vấn";
            if (request.note() != null && !request.note().isBlank()) {
                seedNote += " (Lý do: " + request.note().trim() + ")";
            }
        }
        applicationService.seedStatus(schedule.getApplication(), schedule.getApplication().getStatusEnum(), seedNote);

        return dtoMapper.toInterviewScheduleResponse(saved);
    }

    @Transactional
    public JobOfferResponse createJobOffer(UUID applicationId, UUID employerId, JobOfferRequest request) {
        Application application = getApplicationAndVerifyEmployer(applicationId, employerId);

        Optional<JobOffer> existingOpt = jobOfferRepository.findByApplicationId(applicationId);
        boolean isUpdate = existingOpt.isPresent();
        JobOffer offer = existingOpt.orElseGet(() -> {
            JobOffer newOffer = new JobOffer();
            newOffer.setApplication(application);
            newOffer.setEmployer(application.getJob().getEmployer());
            return newOffer;
        });

        String oldSalaryStr = isUpdate && offer.getSalary() != null ? String.format("%,.0f %s", offer.getSalary().doubleValue(), offer.getSalaryCurrency() != null ? offer.getSalaryCurrency() : "VND") : "Thỏa thuận";
        String oldDateStr = isUpdate && offer.getStartDate() != null ? offer.getStartDate().toString() : "Chưa xác định";

        offer.setPositionTitle(request.positionTitle());
        offer.setSalary(request.salary());
        offer.setSalaryCurrency(request.salaryCurrency() != null ? request.salaryCurrency() : "VND");
        offer.setSalaryType(request.salaryType());
        offer.setStartDate(request.startDate());
        offer.setBenefits(request.benefits());
        offer.setWorkingLocation(request.workingLocation());
        offer.setOfferLetterUrl(request.offerLetterUrl());
        offer.setEmployerNote(request.employerNote());
        offer.setStatus("sent");
        offer.setSentAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));

        JobOffer saved = jobOfferRepository.save(offer);

        if (isUpdate) {
            String newSalaryStr = request.salary() != null ? String.format("%,.0f %s", request.salary().doubleValue(), request.salaryCurrency() != null ? request.salaryCurrency() : "VND") : "Thỏa thuận";
            String newDateStr = request.startDate() != null ? request.startDate().toString() : "Chưa xác định";
            applicationService.seedStatus(application, Application.ApplicationStatus.ACCEPTED,
                    "Nhà tuyển dụng đã cập nhật lại Job Offer (Offer cũ: Lương " + oldSalaryStr + ", Ngày BĐ: " + oldDateStr + " -> Offer mới: Lương " + newSalaryStr + ", Ngày BĐ: " + newDateStr + ")");
        } else {
            String salaryStr = request.salary() != null ? String.format("%,.0f %s", request.salary().doubleValue(), request.salaryCurrency() != null ? request.salaryCurrency() : "VND") : "Thỏa thuận";
            String startDateStr = request.startDate() != null ? request.startDate().toString() : "Chưa xác định";
            applicationService.seedStatus(application, Application.ApplicationStatus.ACCEPTED,
                    "Đã gửi Thư mời nhận việc (Job Offer): " + request.positionTitle() + " (Mức lương: " + salaryStr + ", Ngày bắt đầu: " + startDateStr + ") - Đang chờ ứng viên phản hồi");
        }

        // Send email
        CandidateProfile candidate = application.getCandidate();
        Job job = application.getJob();
        Company company = job.getCompany();

        emailService.sendJobOfferEmail(
                candidate.getUser().getEmail(),
                candidate.getFullName(),
                request.positionTitle(),
                company.getName(),
                company.getContactPhone(),
                company.getContactEmail(),
                request
        );

        return dtoMapper.toJobOfferResponse(saved);
    }

    @Transactional
    public JobOfferResponse candidateRespondToOffer(UUID offerId, UUID candidateId, CandidateOfferResponseRequest request) {
        JobOffer offer = jobOfferRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Không tìm thấy Job Offer"));

        if (!offer.getApplication().getCandidate().getId().equals(candidateId)) {
             throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Không có quyền truy cập");
        }
        if (!"sent".equalsIgnoreCase(offer.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "OFFER_ALREADY_RESPONDED", "Job Offer không còn chờ phản hồi");
        }
        if (offer.getExpiresAt() != null && offer.getExpiresAt().isBefore(LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")))) {
            throw new ApiException(HttpStatus.CONFLICT, "OFFER_EXPIRED", "Job Offer đã hết hạn");
        }

        boolean accepted = request.accepted();
        offer.setCandidateNote(request.note());
        offer.setRespondedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));

        String noteLower = request.note() != null ? request.note().toLowerCase() : "";
        boolean isNegotiation = !accepted && (noteLower.contains("thương lượng") || noteLower.contains("đề xuất") || noteLower.contains("lương") || noteLower.contains("ngày") || (request.note() != null && !request.note().isBlank()));

        if (accepted) {
            offer.setStatus("accepted");
            applicationService.seedStatus(offer.getApplication(), Application.ApplicationStatus.HIRED, "Ứng viên đã chấp nhận Thư mời nhận việc (Job Offer)");
        } else if (isNegotiation) {
            offer.setStatus("rejected");
            applicationService.seedStatus(offer.getApplication(), Application.ApplicationStatus.ACCEPTED, "Ứng viên đề xuất thương lượng Job Offer (Lý do / Đề xuất: " + request.note() + ")");
        } else {
            offer.setStatus("rejected");
            applicationService.seedStatus(offer.getApplication(), Application.ApplicationStatus.REJECTED, "Ứng viên đã từ chối Thư mời nhận việc (Job Offer)");
        }

        JobOffer saved = jobOfferRepository.saveAndFlush(offer);

        // Notify employer
        String responseText = accepted ? "đã chấp nhận Job Offer" : isNegotiation ? "đã gửi đề xuất thương lượng Job Offer" : "đã từ chối Job Offer";
        employerRepository.findByCompanyId(offer.getApplication().getJob().getCompany().getId()).forEach(employer -> {
            if (employer.getUser() != null) {
                createNotification(employer.getUser(), "CANDIDATE_RESPONDED_OFFER", "Ứng viên phản hồi Job Offer",
                        "Ứng viên " + offer.getApplication().getCandidate().getFullName() + " " + responseText + " cho vị trí " + offer.getApplication().getJob().getTitle(), offer.getApplication().getId(), "APPLICATION");
            }
        });

        return dtoMapper.toJobOfferResponse(saved);
    }

    private Application getApplicationAndVerifyEmployer(UUID applicationId, UUID employerId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Không tìm thấy hồ sơ ứng tuyển"));

        if (!application.getJob().getEmployer().getId().equals(employerId)) {
            if (!application.getJob().getCompany().getId().equals(application.getJob().getEmployer().getCompany().getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Không có quyền xử lý hồ sơ này");
            }
        }
        return application;
    }

    @Transactional
    public JobOfferResponse employerRespondToOfferRejection(UUID offerId, UUID employerId, boolean isUpdating, JobOfferRequest updateRequest) {
        JobOffer offer = jobOfferRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Không tìm thấy Job Offer"));

        getApplicationAndVerifyEmployer(offer.getApplication().getId(), employerId);

        if (!"negotiation_requested".equalsIgnoreCase(offer.getStatus()) && !"rejected".equalsIgnoreCase(offer.getStatus()) && !"declined".equalsIgnoreCase(offer.getStatus()) && !"sent".equalsIgnoreCase(offer.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Job Offer không ở trạng thái chờ nhà tuyển dụng xử lý thương lượng");
        }

        if (isUpdating && updateRequest != null) {
            String oldSalaryStr = offer.getSalary() != null ? String.format("%,.0f %s", offer.getSalary().doubleValue(), offer.getSalaryCurrency() != null ? offer.getSalaryCurrency() : "VND") : "Thỏa thuận";
            String oldDateStr = offer.getStartDate() != null ? offer.getStartDate().toString() : "Chưa xác định";

            String newSalaryStr = updateRequest.salary() != null ? String.format("%,.0f %s", updateRequest.salary().doubleValue(), updateRequest.salaryCurrency() != null ? updateRequest.salaryCurrency() : "VND") : "Thỏa thuận";
            String newDateStr = updateRequest.startDate() != null ? updateRequest.startDate().toString() : "Chưa xác định";

            offer.setPositionTitle(updateRequest.positionTitle());
            offer.setSalary(updateRequest.salary());
            offer.setSalaryCurrency(updateRequest.salaryCurrency() != null ? updateRequest.salaryCurrency() : "VND");
            offer.setSalaryType(updateRequest.salaryType());
            offer.setStartDate(updateRequest.startDate());
            offer.setBenefits(updateRequest.benefits());
            offer.setWorkingLocation(updateRequest.workingLocation());
            offer.setOfferLetterUrl(updateRequest.offerLetterUrl());
            offer.setEmployerNote(updateRequest.employerNote());
            offer.setStatus("sent");

            applicationService.seedStatus(offer.getApplication(), Application.ApplicationStatus.ACCEPTED,
                    "Nhà tuyển dụng đã cập nhật lại Job Offer (Offer cũ: Lương " + oldSalaryStr + ", Ngày BĐ: " + oldDateStr + " -> Offer mới: Lương " + newSalaryStr + ", Ngày BĐ: " + newDateStr + ")");

            CandidateProfile candidate = offer.getApplication().getCandidate();
            Job job = offer.getApplication().getJob();
            Company company = job.getCompany();

            emailService.sendJobOfferEmail(
                    candidate.getUser().getEmail(),
                    candidate.getFullName(),
                    updateRequest.positionTitle(),
                    company.getName(),
                    company.getContactPhone(),
                    company.getContactEmail(),
                    updateRequest
            );
        } else {
            offer.setStatus("sent");
            offer.setEmployerNote(updateRequest != null ? updateRequest.employerNote() : "");

            String noteText = (updateRequest != null && updateRequest.employerNote() != null && !updateRequest.employerNote().isBlank()) ? " (Lời nhắn: " + updateRequest.employerNote() + ")" : "";
            applicationService.seedStatus(offer.getApplication(), Application.ApplicationStatus.ACCEPTED, "Nhà tuyển dụng từ chối thương lượng Job Offer (Giữ nguyên Offer cũ" + noteText + ")");
        }

        JobOffer saved = jobOfferRepository.save(offer);
        return dtoMapper.toJobOfferResponse(saved);
    }

    @Transactional
    public JobOfferResponse candidateFinalRespondToOffer(UUID offerId, UUID candidateId, CandidateOfferResponseRequest request) {
        JobOffer offer = jobOfferRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Không tìm thấy Job Offer"));

        if (!offer.getApplication().getCandidate().getId().equals(candidateId)) {
             throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Không có quyền truy cập");
        }

        if (!"employer_declined_negotiation".equalsIgnoreCase(offer.getStatus()) && !"rejected".equalsIgnoreCase(offer.getStatus()) && !"declined".equalsIgnoreCase(offer.getStatus()) && !"sent".equalsIgnoreCase(offer.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Job Offer không ở trạng thái chờ chốt phản hồi");
        }

        boolean accepted = request.accepted();
        offer.setRespondedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        if (accepted) {
            offer.setStatus("accepted");
            applicationService.seedStatus(offer.getApplication(), Application.ApplicationStatus.HIRED, "Ứng viên đã chấp nhận Job Offer ban đầu");
        } else {
            offer.setStatus("rejected");
            applicationService.seedStatus(offer.getApplication(), Application.ApplicationStatus.REJECTED, "Ứng viên đã từ chối Job Offer ban đầu");
        }

        JobOffer saved = jobOfferRepository.saveAndFlush(offer);

        // Notify employer
        String responseText = accepted ? "đã chấp nhận Job Offer" : "quyết định từ chối Job Offer";
        employerRepository.findByCompanyId(offer.getApplication().getJob().getCompany().getId()).forEach(employer -> {
            if (employer.getUser() != null) {
                createNotification(employer.getUser(), "CANDIDATE_FINAL_RESPONDED_OFFER", "Ứng viên chốt phản hồi Job Offer",
                        "Ứng viên " + offer.getApplication().getCandidate().getFullName() + " " + responseText + " cho vị trí " + offer.getApplication().getJob().getTitle(), offer.getApplication().getId(), "APPLICATION");
            }
        });

        return dtoMapper.toJobOfferResponse(saved);
    }

    private void validateNoInterviewTimeConflict(UUID jobId, LocalDateTime proposedTime, UUID currentScheduleId) {
        if (proposedTime == null || jobId == null) return;

        List<InterviewSchedule> activeSchedules = interviewScheduleRepository.findActiveInterviewsByJob(jobId);

        for (InterviewSchedule existing : activeSchedules) {
            if (currentScheduleId != null && existing.getId().equals(currentScheduleId)) {
                continue;
            }
            if (existing.getScheduledAt() != null && existing.getScheduledAt().isAfter(LocalDateTime.now())) {
                long minutesDiff = Math.abs(java.time.Duration.between(existing.getScheduledAt(), proposedTime).toMinutes());
                if (minutesDiff < 30) {
                    String formattedExistingTime = existing.getScheduledAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
                    String candidateName = existing.getCandidate() != null ? existing.getCandidate().getFullName() : "ứng viên khác";
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INTERVIEW_TIME_CONFLICT",
                            "Thời gian phỏng vấn bị trùng hoặc cách lịch phỏng vấn của " + candidateName + " (" + formattedExistingTime + ") dưới 30 phút. Vui lòng chọn khung giờ khác.");
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<com.sjp.recruitment.model.dto.response.OccupiedInterviewSlotResponse> getOccupiedInterviewSlots(UUID jobId, UUID employerId, LocalDate date) {
        List<Application> jobApps = applicationRepository.findAllByJobId(jobId);
        if (!jobApps.isEmpty()) {
            getApplicationAndVerifyEmployer(jobApps.get(0).getId(), employerId);
        }

        LocalDate targetDate = date != null ? date : LocalDate.now();
        LocalDateTime fromTime = targetDate.isEqual(LocalDate.now()) ? LocalDateTime.now() : targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.atTime(23, 59, 59);

        List<InterviewSchedule> activeSchedules = interviewScheduleRepository.findActiveInterviewsByJobAndDate(jobId, fromTime, endOfDay);

        return activeSchedules.stream().map(s -> new com.sjp.recruitment.model.dto.response.OccupiedInterviewSlotResponse(
                s.getId(),
                s.getApplication() != null ? s.getApplication().getId() : null,
                s.getCandidate() != null ? s.getCandidate().getFullName() : "Ứng viên",
                s.getScheduledAt(),
                s.getStatus()
        )).toList();
    }
}
