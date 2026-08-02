-- Allow 'archived' status for soft-deleted jobs (employer delete with existing applications)
ALTER TABLE jobs DROP CONSTRAINT IF EXISTS jobs_status_check;
ALTER TABLE jobs
  ADD CONSTRAINT jobs_status_check
  CHECK (status IN (
    'draft', 'pending_review', 'published', 'rejected',
    'closed', 'expired', 'removed', 'awaiting_company', 'archived'
  ));
