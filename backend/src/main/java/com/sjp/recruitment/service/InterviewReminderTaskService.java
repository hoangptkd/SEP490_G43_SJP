package com.sjp.recruitment.service;

import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.model.entity.Employer;
import com.sjp.recruitment.model.entity.InterviewSchedule;
import com.sjp.recruitment.model.entity.Notification;
import com.sjp.recruitment.repository.EmployerRepository;
import com.sjp.recruitment.repository.InterviewScheduleRepository;
import com.sjp.recruitment.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewReminderTaskService {

    private final InterviewScheduleRepository interviewScheduleRepository;
    private final EmployerRepository employerRepository;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processExpiredSchedule(UUID scheduleId, LocalDateTime now) {
        InterviewSchedule schedule = interviewScheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null || !isExpirable(schedule) || !isExpired(schedule, now)) return;

        schedule.setStatus("NO_RESPONSE");
        interviewScheduleRepository.save(schedule);

        Application application = schedule.getApplication();
        if (application != null && application.getJob() != null
                && application.getJob().getCompany() != null) {
            String candidateName = application.getCandidate() != null
                    ? application.getCandidate().getFullName()
                    : "Ứng viên";
            String jobTitle = application.getJob().getTitle();
            String scheduledTime = schedule.getScheduledAt() != null
                    ? schedule.getScheduledAt().toString()
                    : "Chưa xác định";

            List<Employer> employers = employerRepository
                    .findByCompanyId(application.getJob().getCompany().getId());
            for (Employer employer : employers) {
                if (employer.getUser() == null) continue;

                Notification notification = new Notification();
                notification.setRecipientUser(employer.getUser());
                notification.setType("CANDIDATE_NO_RESPONSE");
                notification.setTitle("Ứng viên không phản hồi lịch phỏng vấn");
                notification.setMessage("Ứng viên " + candidateName
                        + " đã không phản hồi lịch phỏng vấn cho vị trí "
                        + jobTitle + " đúng hạn.");
                notification.setRelatedEntityType("APPLICATION");
                notification.setRelatedEntityId(application.getId());
                notificationRepository.save(notification);

                emailService.sendInterviewNoResponseNotificationToEmployer(
                        employer.getUser().getEmail(),
                        employer.getUser().getFullName(),
                        candidateName,
                        jobTitle,
                        scheduledTime
                );
            }
        }
        log.info("Lịch phỏng vấn {} đã tự động chuyển sang NO_RESPONSE do quá hạn phản hồi",
                schedule.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processReminderSchedule(UUID scheduleId, LocalDateTime now) {
        InterviewSchedule schedule = interviewScheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null || !isReminderEligible(schedule, now)) return;

        int count = schedule.getReminderCount() == null ? 0 : schedule.getReminderCount();
        boolean shouldSend = false;
        boolean urgentSecondReminder = false;

        if (count == 0 && !schedule.getScheduledAt().isAfter(now.plusHours(24))) {
            shouldSend = true;
        } else if (count == 1 && isExpirable(schedule)) {
            LocalDateTime lastReminder = schedule.getLastReminderAt();
            boolean enoughTimePassed = lastReminder == null
                    || lastReminder.isBefore(now.minusHours(6));
            LocalDateTime deadlineOrTime = schedule.getResponseDeadline() != null
                    ? schedule.getResponseDeadline()
                    : schedule.getScheduledAt();
            if (enoughTimePassed
                    && !deadlineOrTime.isAfter(now.plusHours(12))
                    && deadlineOrTime.isAfter(now)) {
                shouldSend = true;
                urgentSecondReminder = true;
            }
        }

        if (shouldSend) {
            sendReminder(schedule, urgentSecondReminder, now, count + 1);
        }
    }

    private boolean isExpirable(InterviewSchedule schedule) {
        return "SCHEDULED".equals(schedule.getStatus())
                || "PENDING_RESPONSE".equals(schedule.getStatus());
    }

    private boolean isExpired(InterviewSchedule schedule, LocalDateTime now) {
        if (schedule.getResponseDeadline() != null
                && !schedule.getResponseDeadline().isAfter(now)) return true;
        return schedule.getScheduledAt() != null && !schedule.getScheduledAt().isAfter(now);
    }

    private boolean isReminderEligible(InterviewSchedule schedule, LocalDateTime now) {
        boolean eligibleStatus = isExpirable(schedule) || "ACCEPTED".equals(schedule.getStatus());
        int count = schedule.getReminderCount() == null ? 0 : schedule.getReminderCount();
        return eligibleStatus
                && schedule.getScheduledAt() != null
                && schedule.getScheduledAt().isAfter(now)
                && count < 2;
    }

    private void sendReminder(
            InterviewSchedule schedule,
            boolean urgentSecondReminder,
            LocalDateTime now,
            int newCount
    ) {
        Application application = schedule.getApplication();
        if (application == null || application.getCandidate() == null
                || application.getJob() == null) return;

        boolean waitingForConfirmation = isExpirable(schedule);
        String reminderNote;
        if (waitingForConfirmation) {
            reminderNote = urgentSecondReminder
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
    }
}
