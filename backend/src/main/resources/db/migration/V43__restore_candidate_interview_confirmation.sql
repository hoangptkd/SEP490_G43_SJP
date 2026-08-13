UPDATE interview_schedules
SET status = 'PENDING_RESPONSE',
    responded_at = NULL,
    response_deadline = NULL,
    last_reminder_at = NULL
WHERE status = 'SCHEDULED';
