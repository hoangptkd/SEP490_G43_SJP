CREATE TABLE IF NOT EXISTS job_offers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id uuid NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    employer_id uuid NOT NULL REFERENCES employers(id) ON DELETE RESTRICT,
    position_title text NOT NULL,
    salary numeric(14,2),
    salary_currency text NOT NULL DEFAULT 'VND',
    salary_type text,
    start_date date,
    benefits text,
    working_location text,
    offer_letter_url text,
    status text NOT NULL DEFAULT 'sent',
    sent_at timestamptz,
    responded_at timestamptz,
    expires_at timestamptz,
    candidate_note text,
    employer_note text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT unique_offer_per_application UNIQUE (application_id)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'job_offers_salary_type_check'
    ) THEN
        ALTER TABLE job_offers
            ADD CONSTRAINT job_offers_salary_type_check
            CHECK (salary_type IS NULL OR salary_type IN ('monthly', 'yearly', 'negotiable'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'job_offers_status_check'
    ) THEN
        ALTER TABLE job_offers
            ADD CONSTRAINT job_offers_status_check
            CHECK (status IN (
                'draft',
                'sent',
                'accepted',
                'rejected',
                'expired',
                'withdrawn',
                'employer_declined_negotiation',
                'withdrawn_by_candidate'
            ));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'jobs_salary_type_check'
    ) THEN
        ALTER TABLE jobs
            ADD CONSTRAINT jobs_salary_type_check
            CHECK (salary_type IN ('range', 'fixed', 'negotiable'));
    END IF;
END $$;

DROP TRIGGER IF EXISTS job_offers_set_updated_at ON job_offers;
CREATE TRIGGER job_offers_set_updated_at
BEFORE UPDATE ON job_offers
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX IF NOT EXISTS idx_job_offers_application_id ON job_offers(application_id);
CREATE INDEX IF NOT EXISTS idx_job_offers_employer_id ON job_offers(employer_id);
CREATE INDEX IF NOT EXISTS idx_job_offers_status ON job_offers(status);

ALTER TABLE interview_schedules
    ADD COLUMN IF NOT EXISTS candidate_response text DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS candidate_response_at timestamptz,
    ADD COLUMN IF NOT EXISTS candidate_reschedule_note text,
    ADD COLUMN IF NOT EXISTS employer_reschedule_response text,
    ADD COLUMN IF NOT EXISTS employer_reschedule_note text,
    ADD COLUMN IF NOT EXISTS employer_reschedule_at timestamptz,
    ADD COLUMN IF NOT EXISTS interview_result text DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS interview_result_note text,
    ADD COLUMN IF NOT EXISTS result_updated_by uuid REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS result_updated_at timestamptz;

UPDATE interview_schedules
SET candidate_response = 'pending'
WHERE candidate_response IS NULL;

UPDATE interview_schedules
SET interview_result = 'pending'
WHERE interview_result IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'interview_schedules_candidate_response_check'
    ) THEN
        ALTER TABLE interview_schedules
            ADD CONSTRAINT interview_schedules_candidate_response_check
            CHECK (candidate_response IS NULL OR candidate_response IN ('pending', 'confirmed', 'request_reschedule', 'declined'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'interview_schedules_interview_result_check'
    ) THEN
        ALTER TABLE interview_schedules
            ADD CONSTRAINT interview_schedules_interview_result_check
            CHECK (interview_result IS NULL OR interview_result IN ('pending', 'pass', 'fail', 'no_show'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'interview_schedules_employer_reschedule_response_check'
    ) THEN
        ALTER TABLE interview_schedules
            ADD CONSTRAINT interview_schedules_employer_reschedule_response_check
            CHECK (employer_reschedule_response IS NULL OR employer_reschedule_response IN ('pending', 'accept_reschedule', 'reject_reschedule'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_interview_schedules_candidate_response ON interview_schedules(candidate_response);
CREATE INDEX IF NOT EXISTS idx_interview_schedules_interview_result ON interview_schedules(interview_result);
