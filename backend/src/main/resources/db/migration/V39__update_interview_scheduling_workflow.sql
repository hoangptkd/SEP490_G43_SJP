ALTER TABLE interview_schedules 
    ADD COLUMN viewed_at TIMESTAMP,
    ADD COLUMN responded_at TIMESTAMP,
    ADD COLUMN response_deadline TIMESTAMP,
    ADD COLUMN last_reminder_at TIMESTAMP;

-- Drop old constraints and columns if needed. But dropping might fail if views depend on them.
-- Just dropping the column in a soft way or altering the status constraint.
ALTER TABLE interview_schedules DROP CONSTRAINT IF EXISTS interview_schedules_status_check;

-- Update existing records to PENDING_RESPONSE if they are 'scheduled' or 'pending'
UPDATE interview_schedules SET status = 'PENDING_RESPONSE' WHERE status IN ('scheduled', 'pending');
UPDATE interview_schedules SET status = 'ACCEPTED' WHERE status = 'confirmed';
UPDATE interview_schedules SET status = 'DECLINED' WHERE status = 'declined';
UPDATE interview_schedules SET status = 'RESCHEDULE_REQUESTED' WHERE status = 'rescheduled';
UPDATE interview_schedules SET status = 'COMPLETED' WHERE interview_result = 'pass';
UPDATE interview_schedules SET status = 'NO_SHOW' WHERE interview_result = 'fail';

ALTER TABLE interview_schedules DROP COLUMN candidate_response;
ALTER TABLE interview_schedules DROP COLUMN candidate_response_at;
ALTER TABLE interview_schedules DROP COLUMN interview_result;
ALTER TABLE interview_schedules DROP COLUMN interview_result_note;
ALTER TABLE interview_schedules DROP COLUMN result_updated_at;
ALTER TABLE interview_schedules DROP COLUMN result_updated_by;
