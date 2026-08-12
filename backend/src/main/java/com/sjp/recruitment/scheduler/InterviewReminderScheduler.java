package com.sjp.recruitment.scheduler;

import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.model.entity.Company;
import com.sjp.recruitment.model.entity.Employer;
import com.sjp.recruitment.model.entity.InterviewSchedule;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.model.entity.Notification;
import com.sjp.recruitment.repository.EmployerRepository;
import com.sjp.recruitment.repository.InterviewScheduleRepository;
import com.sjp.recruitment.repository.NotificationRepository;
import com.sjp.recruitment.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewReminderScheduler {

    private final InterviewScheduleRepository interviewScheduleRepository;
    private final NotificationRepository notificationRepository;
    private final EmployerRepository employerRepository;
    private final EmailService emailService;

    @Scheduled(cron = "0 0/30 * * * *") // Run every 30 minutes
    @Transactional
    public void processInterviewReminders() {
        log.info("Starting InterviewReminderScheduler...");
        LocalDateTime now = LocalDateTime.now();

        // 1. Process NO_RESPONSE timeout
        List<InterviewSchedule> pendingInterviews = interviewScheduleRepository.findAll().stream()
                .filter(i -> "PENDING_RESPONSE".equals(i.getStatus()))
                .collect(Collectors.toList());

        for (InterviewSchedule schedule : pendingInterviews) {
            if (schedule.getResponseDeadline() != null && schedule.getResponseDeadline().isBefore(now)) {
                // Deadline passed, mark as NO_RESPONSE
                schedule.setStatus("NO_RESPONSE");
                interviewScheduleRepository.save(schedule);
                
                // Notify Employer
                Application application = schedule.getApplication();
                if (application != null && application.getJob() != null && application.getJob().getCompany() != null) {
                    employerRepository.findByCompanyId(application.getJob().getCompany().getId()).forEach(employer -> {
                        if (employer.getUser() != null) {
                            Notification n = new Notification();
                            n.setRecipientUser(employer.getUser());
                            n.setType("INTERVIEW_NO_RESPONSE");
                            n.setTitle("⚠️ Candidate has not responded");
                            n.setMessage("Ứng viên " + application.getCandidate().getFullName() + " không phản hồi lịch phỏng vấn trước hạn chót.");
                            n.setRelatedEntityType("APPLICATION");
                            n.setRelatedEntityId(application.getId());
                            notificationRepository.save(n);
                        }
                    });
                }
                log.info("Interview {} moved to NO_RESPONSE", schedule.getId());
                continue;
            }

            // 2. Process Reminders (Max 2 reminders)
            // Determine if it's time for a reminder.
            // Simplified logic: Send Reminder 1 if we are halfway to deadline and no reminder was sent yet.
            // Send Reminder 2 if we are 2 hours away from deadline and last reminder was before that.
            if (schedule.getResponseDeadline() != null && schedule.getCreatedAt() != null) {
                long totalMillis = java.time.Duration.between(schedule.getCreatedAt(), schedule.getResponseDeadline()).toMillis();
                LocalDateTime halfway = schedule.getCreatedAt().plus(java.time.Duration.ofMillis(totalMillis / 2));
                LocalDateTime twoHoursBefore = schedule.getResponseDeadline().minusHours(2);
                
                boolean shouldSendReminder = false;
                
                if (now.isAfter(twoHoursBefore)) {
                    // Time for final reminder (if last reminder was not already sent recently)
                    if (schedule.getLastReminderAt() == null || schedule.getLastReminderAt().isBefore(twoHoursBefore.minusHours(1))) {
                        shouldSendReminder = true;
                    }
                } else if (now.isAfter(halfway)) {
                    // Time for first reminder
                    if (schedule.getLastReminderAt() == null) {
                        shouldSendReminder = true;
                    }
                }

                if (shouldSendReminder) {
                    schedule.setLastReminderAt(now);
                    interviewScheduleRepository.save(schedule);
                    
                    Application application = schedule.getApplication();
                    if (application != null) {
                        try {
                            emailService.sendInterviewInvitationEmail(
                                application.getCandidate().getUser().getEmail(),
                                application.getCandidate().getFullName(),
                                application.getJob().getTitle(),
                                application.getJob().getCompany().getName(),
                                schedule.getScheduledAt().toString(),
                                schedule.getLocation(),
                                schedule.getMeetingLink(),
                                "Reminder: Vui lòng xác nhận lịch phỏng vấn trước " + schedule.getResponseDeadline().toString() + "\n\n" + schedule.getNote()
                            );
                            log.info("Sent reminder for Interview {}", schedule.getId());
                        } catch (Exception e) {
                            log.error("Failed to send reminder email", e);
                        }
                    }
                }
            }
        }
        log.info("Finished InterviewReminderScheduler.");
    }
}
