package com.sjp.recruitment.scheduler;

import com.sjp.recruitment.repository.InterviewScheduleRepository;
import com.sjp.recruitment.service.InterviewReminderTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewReminderSchedulerTest {

    @Mock
    private InterviewScheduleRepository interviewScheduleRepository;

    @Mock
    private InterviewReminderTaskService reminderTaskService;

    @Test
    void skipsOneBrokenExpiryRecordAndContinuesWithTheNextRecord() {
        UUID brokenId = UUID.randomUUID();
        UUID validId = UUID.randomUUID();
        when(interviewScheduleRepository.findExpiredWorkflowIds(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of(brokenId, validId));
        doThrow(new IllegalArgumentException("invalid profile json"))
                .when(reminderTaskService).processExpiredSchedule(
                        eq(brokenId), any(LocalDateTime.class));

        InterviewReminderScheduler scheduler = new InterviewReminderScheduler(
                interviewScheduleRepository, reminderTaskService);

        scheduler.processExpiredInterviews();

        InOrder order = inOrder(reminderTaskService);
        order.verify(reminderTaskService).processExpiredSchedule(
                eq(brokenId), any(LocalDateTime.class));
        order.verify(reminderTaskService).processExpiredSchedule(
                eq(validId), any(LocalDateTime.class));
    }

    @Test
    void skipsOneBrokenReminderRecordAndContinuesWithTheNextRecord() {
        UUID brokenId = UUID.randomUUID();
        UUID validId = UUID.randomUUID();
        when(interviewScheduleRepository.findReminderWorkflowIds(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of(brokenId, validId));
        doThrow(new IllegalStateException("invalid record"))
                .when(reminderTaskService).processReminderSchedule(
                        eq(brokenId), any(LocalDateTime.class));

        InterviewReminderScheduler scheduler = new InterviewReminderScheduler(
                interviewScheduleRepository, reminderTaskService);

        scheduler.processInterviewReminders();

        verify(reminderTaskService).processReminderSchedule(
                eq(validId), any(LocalDateTime.class));
    }
}
