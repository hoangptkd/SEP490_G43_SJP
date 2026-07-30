-- Job waiting for company to fix after admin notification
ALTER TABLE jobs DROP CONSTRAINT IF EXISTS jobs_status_check;
ALTER TABLE jobs
  ADD CONSTRAINT jobs_status_check
  CHECK (status IN (
    'draft', 'pending_review', 'published', 'rejected', 'closed', 'expired', 'removed', 'awaiting_company'
  ));

ALTER TABLE job_reports DROP CONSTRAINT IF EXISTS job_reports_status_check;
ALTER TABLE job_reports
  ADD CONSTRAINT job_reports_status_check
  CHECK (status IN ('pending', 'dismissed', 'resolved', 'awaiting_company', 'resubmitted'));
