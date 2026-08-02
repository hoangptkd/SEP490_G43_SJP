-- Deadline for company to fix a reported job after admin notify (3 days)
ALTER TABLE jobs
  ADD COLUMN IF NOT EXISTS report_fix_deadline timestamptz;

ALTER TABLE job_reports
  ADD COLUMN IF NOT EXISTS company_fix_deadline timestamptz;

CREATE INDEX IF NOT EXISTS idx_jobs_report_fix_deadline
  ON jobs (report_fix_deadline)
  WHERE status = 'awaiting_company' AND report_fix_deadline IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_job_reports_company_fix_deadline
  ON job_reports (company_fix_deadline)
  WHERE status = 'awaiting_company' AND company_fix_deadline IS NOT NULL;
