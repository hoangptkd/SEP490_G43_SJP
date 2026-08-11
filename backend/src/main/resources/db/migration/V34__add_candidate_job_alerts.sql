CREATE TABLE IF NOT EXISTS job_alerts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_seeker_id uuid NOT NULL REFERENCES job_seekers(id) ON DELETE CASCADE,
    name varchar(120) NOT NULL,
    keyword varchar(160),
    location varchar(160),
    category varchar(120),
    job_type varchar(32),
    work_mode varchar(32),
    min_salary numeric(14,2),
    max_salary numeric(14,2),
    frequency varchar(16) NOT NULL DEFAULT 'DAILY',
    enabled boolean NOT NULL DEFAULT true,
    last_run_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz DEFAULT now(),
    CONSTRAINT job_alert_salary_range_check CHECK (min_salary IS NULL OR max_salary IS NULL OR min_salary <= max_salary),
    CONSTRAINT job_alert_frequency_check CHECK (frequency IN ('DAILY', 'WEEKLY'))
);

CREATE INDEX IF NOT EXISTS idx_job_alerts_candidate_created ON job_alerts(job_seeker_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_job_alerts_due ON job_alerts(enabled, last_run_at);

CREATE TABLE IF NOT EXISTS job_alert_matches (
    alert_id uuid NOT NULL REFERENCES job_alerts(id) ON DELETE CASCADE,
    job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (alert_id, job_id)
);
