package com.sjp.recruitment.scheduler;

import com.sjp.recruitment.repository.InterviewScheduleRepository;
import com.sjp.recruitment.service.InterviewReminderTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InterviewReminderScheduler {

    private static final List<String> EXPIRABLE_STATUSES = List.of(
            "SCHEDULED", "PENDING_RESPONSE");
    private static final List<String> REMINDER_STATUSES = List.of(
            "SCHEDULED", "PENDING_RESPONSE", "ACCEPTED");

    private final InterviewScheduleRepository interviewScheduleRepository;
    private final InterviewReminderTaskService reminderTaskService;

    @Scheduled(cron = "0 0/15 * * * *")
    public void processWorkflowTasks() {
        processExpiredInterviews();
        processInterviewReminders();
    }

    public void processExpiredInterviews() {
        LocalDateTime now = LocalDateTime.now();
        List<UUID> scheduleIds = interviewScheduleRepository
                .findExpiredWorkflowIds(EXPIRABLE_STATUSES, now);
        for (UUID scheduleId : scheduleIds) {
            runIsolated(scheduleId, "expiry",
                    () -> reminderTaskService.processExpiredSchedule(scheduleId, now));
        }
    }

    public void processInterviewReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<UUID> scheduleIds = interviewScheduleRepository
                .findReminderWorkflowIds(REMINDER_STATUSES, now);
        for (UUID scheduleId : scheduleIds) {
            runIsolated(scheduleId, "reminder",
                    () -> reminderTaskService.processReminderSchedule(scheduleId, now));
        }
    }

    private void runIsolated(UUID scheduleId, String task, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.warn("Interview schedule task skipped: scheduleId={}, task={}, exceptionType={}",
                    scheduleId, task, rootCauseType(exception));
        }
    }

    private String rootCauseType(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }
}
