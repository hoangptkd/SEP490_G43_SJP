ALTER TABLE interview_schedules
    ADD COLUMN IF NOT EXISTS reminder_count INTEGER NOT NULL DEFAULT 0;
