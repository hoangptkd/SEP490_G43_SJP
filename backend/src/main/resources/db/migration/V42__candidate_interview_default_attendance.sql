UPDATE interview_schedules
SET status = 'SCHEDULED',
    response_deadline = NULL,
    last_reminder_at = NULL
WHERE status IN ('PENDING_RESPONSE', 'ACCEPTED', 'NO_RESPONSE');
