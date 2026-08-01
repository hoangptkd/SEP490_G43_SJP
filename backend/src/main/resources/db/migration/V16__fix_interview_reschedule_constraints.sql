-- Drop the hibernate-generated check constraint that is causing issues
ALTER TABLE interview_schedules DROP CONSTRAINT IF EXISTS interview_schedules_employer_reschedule_response_check;
ALTER TABLE interview_schedules DROP CONSTRAINT IF EXISTS interview_schedules_candidate_response_check;
