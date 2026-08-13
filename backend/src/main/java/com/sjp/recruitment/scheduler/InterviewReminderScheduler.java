package com.sjp.recruitment.scheduler;

import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.model.entity.InterviewSchedule;
import com.sjp.recruitment.repository.InterviewScheduleRepository;
import com.sjp.recruitment.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class InterviewReminderScheduler {
    private final InterviewScheduleRepository interviewScheduleRepository;
    private final EmailService emailService;

    @Scheduled(cron = "0 0/30 * * * *")
    @Transactional
    public void processInterviewReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reminderWindow = now.plusHours(24);

        interviewScheduleRepository.findAll().stream()
                .filter(schedule -> "SCHEDULED".equals(schedule.getStatus()))
                .filter(schedule -> schedule.getScheduledAt() != null
                        && schedule.getScheduledAt().isAfter(now)
                        && !schedule.getScheduledAt().isAfter(reminderWindow))
                .filter(schedule -> schedule.getLastReminderAt() == null)
                .forEach(this::sendReminder);
    }

    private void sendReminder(InterviewSchedule schedule) {
        Application application = schedule.getApplication();
        if (application == null || application.getCandidate() == null || application.getJob() == null) {
            return;
        }
        try {
            emailService.sendInterviewInvitationEmail(
                    application.getCandidate().getUser().getEmail(),
                    application.getCandidate().getFullName(),
                    application.getJob().getTitle(),
                    application.getJob().getCompany().getName(),
                    schedule.getScheduledAt().toString(),
                    schedule.getLocation(),
                    schedule.getMeetingLink(),
                    "Nhắc lịch phỏng vấn: bạn được mặc định tham gia. Nếu không thể tham dự, hãy xin đổi lịch hoặc từ chối trên hệ thống.\n\n"
                            + (schedule.getNote() == null ? "" : schedule.getNote())
            );
            schedule.setLastReminderAt(LocalDateTime.now());
            interviewScheduleRepository.save(schedule);
        } catch (RuntimeException exception) {
            log.warn("Không thể gửi nhắc lịch phỏng vấn {}", schedule.getId(), exception);
        }
    }
}
