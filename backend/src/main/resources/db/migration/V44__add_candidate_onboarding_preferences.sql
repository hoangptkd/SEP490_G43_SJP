ALTER TABLE job_seekers
    ADD COLUMN IF NOT EXISTS desired_job_titles jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS expected_salary numeric(14, 2),
    ADD COLUMN IF NOT EXISTS preferred_locations jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS willing_to_relocate boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS onboarding_status text NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS onboarding_completed_at timestamptz;

UPDATE job_seekers
SET onboarding_status = 'COMPLETED',
    onboarding_completed_at = COALESCE(onboarding_completed_at, updated_at, created_at, now())
WHERE onboarding_status = 'PENDING';

ALTER TABLE job_seekers
    DROP CONSTRAINT IF EXISTS job_seekers_onboarding_status_check;

ALTER TABLE job_seekers
    ADD CONSTRAINT job_seekers_onboarding_status_check
    CHECK (onboarding_status IN ('PENDING', 'COMPLETED', 'SKIPPED'));
