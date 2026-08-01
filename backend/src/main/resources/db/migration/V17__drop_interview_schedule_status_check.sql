-- Drop the explicit check constraint for status on interview_schedules because we added new statuses (rescheduled_rejected, etc)
ALTER TABLE interview_schedules DROP CONSTRAINT IF EXISTS interview_schedules_status_check;
