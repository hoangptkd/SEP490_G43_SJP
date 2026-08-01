ALTER TABLE applications
  ADD COLUMN IF NOT EXISTS job_snapshot_json jsonb;
