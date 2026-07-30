-- Allow removed status for jobs deleted due to confirmed violation
ALTER TABLE jobs DROP CONSTRAINT IF EXISTS jobs_status_check;
ALTER TABLE jobs
  ADD CONSTRAINT jobs_status_check
  CHECK (status IN ('draft', 'pending_review', 'published', 'rejected', 'closed', 'expired', 'removed'));

CREATE TABLE IF NOT EXISTS job_reports (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
  reporter_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  reason text NOT NULL,
  description text,
  status text NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'dismissed', 'resolved')),
  admin_note text,
  resolved_by uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  resolved_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_job_reports_status_created
  ON job_reports (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_job_reports_job
  ON job_reports (job_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_job_reports_pending_per_user
  ON job_reports (job_id, reporter_user_id)
  WHERE status = 'pending';
