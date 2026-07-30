-- Snapshot reporter contact info at report time for admin review
ALTER TABLE job_reports
  ADD COLUMN IF NOT EXISTS reporter_full_name text,
  ADD COLUMN IF NOT EXISTS reporter_phone text,
  ADD COLUMN IF NOT EXISTS reporter_date_of_birth date;
