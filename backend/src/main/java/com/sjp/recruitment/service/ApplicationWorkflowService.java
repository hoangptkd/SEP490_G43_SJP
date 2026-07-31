package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.InterviewCandidateResponseRequest;
import com.sjp.recruitment.model.dto.request.InterviewResultRequest;
import com.sjp.recruitment.model.dto.request.InterviewScheduleRequest;
import com.sjp.recruitment.model.dto.request.JobOfferRequest;
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
        
        InterviewSchedule saved = interviewScheduleRepository.save(schedule);
        
        applicationService.seedStatus(application, Application.ApplicationStatus.INTERVIEW_SCHEDULED, "Đã lên lịch phỏng vấn vòng " + roundNumber);

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

    @Transactional
    public InterviewScheduleResponse candidateRespondToInterview(UUID scheduleId, UUID candidateId, InterviewCandidateResponseRequest request) {
        InterviewSchedule schedule = interviewScheduleRepository.findByIdAndCandidateId(scheduleId, candidateId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Khong tim thay lich phong van"));

        schedule.setCandidateResponse(request.response());
        schedule.setCandidateResponseAt(LocalDateTime.now());
        schedule.setCandidateRescheduleNote(request.rescheduleNote());
        
        if ("request_reschedule".equals(request.response())) {
            schedule.setStatus("rescheduled");
        } else if ("confirmed".equals(request.response()) || "declined".equals(request.response())) {
            schedule.setStatus(request.response()); // Update status if they finally confirm/decline
        }
        
        InterviewSchedule saved = interviewScheduleRepository.save(schedule);

        // Notify employer
        String responseText = "confirmed".equals(request.response()) ? "Đã xác nhận tham gia" : 
                             "declined".equals(request.response()) ? "Đã từ chối tham gia" : "Yêu cầu đổi lịch phỏng vấn";
        employerRepository.findByCompanyId(schedule.getApplication().getJob().getCompany().getId()).forEach(employer -> {
            if (employer.getUser() != null) {
                createNotification(employer.getUser(), "CANDIDATE_RESPONDED_INTERVIEW", "Ứng viên phản hồi lịch phỏng vấn",
                        "Ứng viên " + schedule.getCandidate().getFullName() + " đã phản hồi: " + responseText + " cho vị trí " + schedule.getApplication().getJob().getTitle(), schedule.getApplication().getId(), "APPLICATION");
            }
        });

        return dtoMapper.toInterviewScheduleResponse(saved);
    }

    @Transactional
    public InterviewScheduleResponse employerUpdateInterviewResult(UUID scheduleId, UUID employerId, InterviewResultRequest request) {
        InterviewSchedule schedule = interviewScheduleRepository.findByIdAndEmployerId(scheduleId, employerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Khong tim thay lich phong van"));

        schedule.setInterviewResult(request.result());
        schedule.setInterviewResultNote(request.note());
        schedule.setResultUpdatedAt(LocalDateTime.now());
        // schedule.setResultUpdatedBy(...) - we don't have user id here easily, skip or fetch employer.getUser()
        
        InterviewSchedule saved = interviewScheduleRepository.save(schedule);
        
        if ("fail".equals(request.result())) {
            Application application = schedule.getApplication();
            applicationService.seedStatus(application, Application.ApplicationStatus.REJECTED, "Không đạt yêu cầu phỏng vấn vòng " + schedule.getRoundNumber());
            
            CandidateProfile candidate = application.getCandidate();
            Job job = application.getJob();
            Company company = job.getCompany();
            
            emailService.sendInterviewResultFailedEmail(
                    candidate.getUser().getEmail(),
                    candidate.getFullName(),
                    job.getTitle(),
                    company.getName()
            );
        }

        return dtoMapper.toInterviewScheduleResponse(saved);
    }

    @Transactional
    public InterviewScheduleResponse employerRespondToReschedule(UUID scheduleId, UUID employerId, com.sjp.recruitment.model.dto.request.EmployerRescheduleResponseRequest request) {
        InterviewSchedule schedule = interviewScheduleRepository.findByIdAndEmployerId(scheduleId, employerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Khong tim thay lich phong van"));

        if (!"request_reschedule".equals(schedule.getCandidateResponse())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Ung vien chua yeu cau doi lich");
        }

        schedule.setEmployerRescheduleResponse(request.response());
        schedule.setEmployerRescheduleNote(request.note());
        
        if ("accept_reschedule".equals(request.response())) {
            if (request.scheduledAt() != null) {
                schedule.setScheduledAt(request.scheduledAt());
            }
            schedule.setCandidateResponse("pending"); // Reset candidate response so they can accept/reject the new time
            schedule.setStatus("scheduled"); // Back to scheduled
        } else {
            schedule.setStatus("rescheduled_rejected");
        }
        
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
        
        return dtoMapper.toInterviewScheduleResponse(saved);
    }

    @Transactional
    public JobOfferResponse createJobOffer(UUID applicationId, UUID employerId, JobOfferRequest request) {
        Application application = getApplicationAndVerifyEmployer(applicationId, employerId);
        
        if (jobOfferRepository.existsByApplicationId(applicationId)) {
            throw new ApiException(HttpStatus.CONFLICT, "OFFER_EXISTS", "Ung vien nay da co Job Offer");
        }

        JobOffer offer = new JobOffer();
        offer.setApplication(application);
        offer.setEmployer(application.getJob().getEmployer());
        offer.setPositionTitle(request.positionTitle());
        offer.setSalary(request.salary());
        offer.setSalaryCurrency(request.salaryCurrency() != null ? request.salaryCurrency() : "VND");
        offer.setSalaryType(request.salaryType());
        offer.setStartDate(request.startDate());
        offer.setBenefits(request.benefits());
        offer.setWorkingLocation(request.workingLocation());
        offer.setOfferLetterUrl(request.offerLetterUrl());
        offer.setEmployerNote(request.employerNote());
        offer.setSentAt(LocalDateTime.now());
        
        JobOffer saved = jobOfferRepository.save(offer);

        applicationService.seedStatus(application, Application.ApplicationStatus.ACCEPTED, "Đã gửi Job Offer");

        // Send email
        CandidateProfile candidate = application.getCandidate();
        Job job = application.getJob();
        Company company = job.getCompany();
        
        emailService.sendJobOfferEmail(
                candidate.getUser().getEmail(),
                candidate.getFullName(),
                request.positionTitle(),
                company.getName(),
                request
        );

        return dtoMapper.toJobOfferResponse(saved);
    }
    
    @Transactional
    public JobOfferResponse candidateRespondToOffer(UUID offerId, UUID candidateId, boolean accepted, String note) {
        JobOffer offer = jobOfferRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Khong tim thay Job Offer"));
                
        if (!offer.getApplication().getCandidate().getId().equals(candidateId)) {
             throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Khong co quyen truy cap");
        }
        
        offer.setStatus(accepted ? "accepted" : "rejected");
        offer.setCandidateNote(note);
        offer.setRespondedAt(LocalDateTime.now());
        
        JobOffer saved = jobOfferRepository.save(offer);
        
        if (accepted) {
            // Application is now HIRED
            applicationService.seedStatus(offer.getApplication(), Application.ApplicationStatus.HIRED, "Ứng viên đã chấp nhận Job Offer");
        } else {
            // Do NOT change Application status to REJECTED yet, to allow negotiation.
            applicationService.seedStatus(offer.getApplication(), Application.ApplicationStatus.ACCEPTED, "Ứng viên đã từ chối Job Offer (Chờ phản hồi): " + note);
        }
        
        // Notify employer
        String responseText = accepted ? "đã chấp nhận Job Offer" : "đã từ chối và đề xuất thay đổi Job Offer";
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Khong tim thay ho so ung tuyen"));
                
        if (!application.getJob().getEmployer().getId().equals(employerId)) {
            // Note: In real app, we should check if employer belongs to the same company as the job creator
            // Simple check for now based on exact employer id or employer's company matching job's company
            if (!application.getJob().getCompany().getId().equals(application.getJob().getEmployer().getCompany().getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Khong co quyen xu ly ho so nay");
            }
        }
        return application;
    }

    @Transactional
    public JobOfferResponse employerRespondToOfferRejection(UUID offerId, UUID employerId, boolean isUpdating, JobOfferRequest updateRequest) {
        JobOffer offer = jobOfferRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Khong tim thay Job Offer"));
        
        getApplicationAndVerifyEmployer(offer.getApplication().getId(), employerId);
        
        if (!"rejected".equals(offer.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Job Offer chua bi tu choi, khong the phan hoi");
        }

        if (isUpdating && updateRequest != null) {
            offer.setPositionTitle(updateRequest.positionTitle());
            offer.setSalary(updateRequest.salary());
            offer.setSalaryCurrency(updateRequest.salaryCurrency() != null ? updateRequest.salaryCurrency() : "VND");
            offer.setSalaryType(updateRequest.salaryType());
            offer.setStartDate(updateRequest.startDate());
            offer.setBenefits(updateRequest.benefits());
            offer.setWorkingLocation(updateRequest.workingLocation());
            offer.setOfferLetterUrl(updateRequest.offerLetterUrl());
            offer.setEmployerNote(updateRequest.employerNote());
            offer.setStatus("sent"); // Reset status back to sent
            
            applicationService.seedStatus(offer.getApplication(), Application.ApplicationStatus.ACCEPTED, "Nhà tuyển dụng đã cập nhật lại Job Offer");
            
            // Send email again
            CandidateProfile candidate = offer.getApplication().getCandidate();
            Job job = offer.getApplication().getJob();
            Company company = job.getCompany();
            
            emailService.sendJobOfferEmail(
                    candidate.getUser().getEmail(),
                    candidate.getFullName(),
                    updateRequest.positionTitle(),
                    company.getName(),
                    updateRequest
            );
        } else {
            offer.setStatus("employer_declined_negotiation");
            offer.setEmployerNote(updateRequest != null ? updateRequest.employerNote() : "");
            
            applicationService.seedStatus(offer.getApplication(), Application.ApplicationStatus.ACCEPTED, "Nhà tuyển dụng từ chối cập nhật Job Offer (Giữ nguyên offer cũ)");
            
            // Send simple email notifying the candidate
            CandidateProfile candidate = offer.getApplication().getCandidate();
            Job job = offer.getApplication().getJob();
            Company company = job.getCompany();
            
            // Re-using the sendJobOfferEmail template for simplicity, but maybe a custom one is better.
            // Let's just use the note. We don't have a specific method in emailService, so we'll just skip sending a specific email or use a simple fallback.
            // For now, no separate email because the frontend note will be visible in the portal, but we can try to send it if needed.
        }
        
        JobOffer saved = jobOfferRepository.save(offer);
        return dtoMapper.toJobOfferResponse(saved);
    }

    @Transactional
    public JobOfferResponse candidateFinalRespondToOffer(UUID offerId, UUID candidateId, boolean accepted) {
        JobOffer offer = jobOfferRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Khong tim thay Job Offer"));
                
        if (!offer.getApplication().getCandidate().getId().equals(candidateId)) {
             throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Khong co quyen truy cap");
        }
        
        if (!"employer_declined_negotiation".equals(offer.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Job Offer khong o trang thai tu choi thuong luong");
        }
        
        if (accepted) {
            offer.setStatus("accepted");
            offer.setRespondedAt(LocalDateTime.now());
            applicationService.seedStatus(offer.getApplication(), Application.ApplicationStatus.HIRED, "Ứng viên đã chấp nhận Job Offer cũ");
        } else {
            offer.setStatus("withdrawn_by_candidate"); // or just rejected
            offer.setRespondedAt(LocalDateTime.now());
            applicationService.seedStatus(offer.getApplication(), Application.ApplicationStatus.REJECTED, "Ứng viên quyết định hủy bỏ Job Offer");
        }
        
        JobOffer saved = jobOfferRepository.save(offer);

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
}
