package com.sjp.recruitment.scheduler;

import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.model.entity.Employer;
import com.sjp.recruitment.model.entity.InterviewSchedule;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class InterviewReminderScheduler {
    private final InterviewScheduleRepository interviewScheduleRepository;
    private final EmployerRepository employerRepository;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    @Scheduled(cron = "0 0/15 * * * *")
    @Transactional
    public void processWorkflowTasks() {
        processExpiredInterviews();
        processInterviewReminders();
    }

    @Transactional
    public void processExpiredInterviews() {
        LocalDateTime now = LocalDateTime.now();

        List<InterviewSchedule> unrespondedSchedules = interviewScheduleRepository.findAll().stream()
                .filter(schedule -> "SCHEDULED".equals(schedule.getStatus()) || "PENDING_RESPONSE".equals(schedule.getStatus()))
                .filter(schedule -> isExpired(schedule, now))
                .toList();

        for (InterviewSchedule schedule : unrespondedSchedules) {
            schedule.setStatus("NO_RESPONSE");
            interviewScheduleRepository.save(schedule);

            Application application = schedule.getApplication();
            if (application != null && application.getJob() != null && application.getJob().getCompany() != null) {
                String candidateName = application.getCandidate() != null ? application.getCandidate().getFullName() : "Ứng viên";
                String jobTitle = application.getJob().getTitle();
                String scheduledTime = schedule.getScheduledAt() != null ? schedule.getScheduledAt().toString() : "Chưa xác định";

                // Notify employer in-app
                List<Employer> employers = employerRepository.findByCompanyId(application.getJob().getCompany().getId());
                for (Employer employer : employers) {
                    if (employer.getUser() != null) {
                        Notification notification = new Notification();
                        notification.setRecipientUser(employer.getUser());
                        notification.setType("CANDIDATE_NO_RESPONSE");
                        notification.setTitle("Ứng viên không phản hồi lịch phỏng vấn");
                        notification.setMessage("Ứng viên " + candidateName + " đã không phản hồi lịch phỏng vấn cho vị trí " + jobTitle + " đúng hạn.");
                        notification.setRelatedEntityType("APPLICATION");
                        notification.setRelatedEntityId(application.getId());
                        notificationRepository.save(notification);

                        // Send email to employer
                        emailService.sendInterviewNoResponseNotificationToEmployer(
                                employer.getUser().getEmail(),
                                employer.getUser().getFullName(),
                                candidateName,
                                jobTitle,
                                scheduledTime
                        );
                    }
                }
            }
            log.info("Lịch phỏng vấn {} đã tự động chuyển sang NO_RESPONSE do quá hạn phản hồi", schedule.getId());
        }
    }

    private boolean isExpired(InterviewSchedule schedule, LocalDateTime now) {
        if (schedule.getResponseDeadline() != null && !schedule.getResponseDeadline().isAfter(now)) {
            return true;
        }
        if (schedule.getScheduledAt() != null && !schedule.getScheduledAt().isAfter(now)) {
            return true;
        }
        return false;
    }

    @Transactional
    public void processInterviewReminders() {
        LocalDateTime now = LocalDateTime.now();

        interviewScheduleRepository.findAll().stream()
                .filter(schedule -> "SCHEDULED".equals(schedule.getStatus())
                        || "PENDING_RESPONSE".equals(schedule.getStatus())
                        || "ACCEPTED".equals(schedule.getStatus()))
                .filter(schedule -> schedule.getScheduledAt() != null && schedule.getScheduledAt().isAfter(now))
                .filter(schedule -> (schedule.getReminderCount() == null ? 0 : schedule.getReminderCount()) < 2)
                .forEach(schedule -> sendReminderIfNeeded(schedule, now));
    }

    private void sendReminderIfNeeded(InterviewSchedule schedule, LocalDateTime now) {
        int count = schedule.getReminderCount() == null ? 0 : schedule.getReminderCount();
        boolean shouldSend = false;
        boolean isUrgentSecondReminder = false;

        if (count == 0) {
            // Reminder 1: When within 24h of scheduledAt
            if (!schedule.getScheduledAt().isAfter(now.plusHours(24))) {
                shouldSend = true;
            }
        } else if (count == 1) {
            // Reminder 2: Only for unresponded schedules when approaching responseDeadline / scheduledAt (e.g. within 12h)
            // and at least 6h after reminder 1
            boolean isUnresponded = "SCHEDULED".equals(schedule.getStatus()) || "PENDING_RESPONSE".equals(schedule.getStatus());
            if (isUnresponded) {
                LocalDateTime lastReminder = schedule.getLastReminderAt();
                boolean enoughTimePassed = lastReminder == null || lastReminder.isBefore(now.minusHours(6));
                LocalDateTime deadlineOrTime = schedule.getResponseDeadline() != null ? schedule.getResponseDeadline() : schedule.getScheduledAt();
                
                if (enoughTimePassed && !deadlineOrTime.isAfter(now.plusHours(12)) && deadlineOrTime.isAfter(now)) {
                    shouldSend = true;
                    isUrgentSecondReminder = true;
                }
            }
        }

        if (shouldSend) {
            sendReminder(schedule, isUrgentSecondReminder, now, count + 1);
        }
    }

    private void sendReminder(InterviewSchedule schedule, boolean isUrgentSecondReminder, LocalDateTime now, int newCount) {
        Application application = schedule.getApplication();
        if (application == null || application.getCandidate() == null || application.getJob() == null) {
            return;
        }
        try {
            boolean waitingForConfirmation = "SCHEDULED".equals(schedule.getStatus())
                    || "PENDING_RESPONSE".equals(schedule.getStatus());

            String reminderNote;
            if (waitingForConfirmation) {
                reminderNote = isUrgentSecondReminder
                        ? "CẢNH BÁO HẾT HẠN: Bạn vẫn chưa xác nhận tham gia phỏng vấn. Lịch phỏng vấn sắp hết hạn phản hồi. Vui lòng xác nhận hoặc từ chối ngay trên hệ thống."
                        : "Nhắc lịch phỏng vấn: Bạn chưa xác nhận tham gia. Vui lòng xác nhận, xin đổi lịch hoặc từ chối trên hệ thống.";
            } else {
                reminderNote = "Nhắc lịch phỏng vấn: Bạn đã xác nhận tham gia. Vui lòng có mặt đúng giờ.";
            }

            emailService.sendInterviewInvitationEmail(
                    application.getCandidate().getUser().getEmail(),
                    application.getCandidate().getFullName(),
                    application.getJob().getTitle(),
                    application.getJob().getCompany().getName(),
                    schedule.getScheduledAt().toString(),
                    schedule.getLocation(),
                    schedule.getMeetingLink(),
                    reminderNote + "\n\n" + (schedule.getNote() == null ? "" : schedule.getNote())
            );

            schedule.setLastReminderAt(now);
            schedule.setReminderCount(newCount);
            interviewScheduleRepository.save(schedule);
        } catch (RuntimeException exception) {
            log.warn("Không thể gửi nhắc lịch phỏng vấn {}", schedule.getId(), exception);
        }
    }
}
