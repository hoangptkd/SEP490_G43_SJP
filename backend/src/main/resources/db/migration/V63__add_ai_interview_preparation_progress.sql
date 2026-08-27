CREATE TABLE ai_interview_preparation_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_seeker_id uuid NOT NULL REFERENCES job_seekers(id) ON DELETE CASCADE,
    session_id uuid REFERENCES interview_sessions(id) ON DELETE SET NULL,
    status text NOT NULL DEFAULT 'QUEUED',
    stage text NOT NULL DEFAULT 'VALIDATING',
    progress integer NOT NULL DEFAULT 5,
    message text NOT NULL,
    warning_message text,
    error_code text,
    error_message text,
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ai_interview_preparation_jobs_candidate_idx
    ON ai_interview_preparation_jobs(job_seeker_id, created_at DESC);

CREATE INDEX ai_interview_preparation_jobs_session_idx
    ON ai_interview_preparation_jobs(session_id)
    WHERE session_id IS NOT NULL;

DROP TRIGGER IF EXISTS ai_interview_preparation_jobs_set_updated_at
    ON ai_interview_preparation_jobs;
CREATE TRIGGER ai_interview_preparation_jobs_set_updated_at
BEFORE UPDATE ON ai_interview_preparation_jobs
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
