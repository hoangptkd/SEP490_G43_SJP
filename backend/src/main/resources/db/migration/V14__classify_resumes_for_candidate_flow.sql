ALTER TABLE resumes
  ADD COLUMN IF NOT EXISTS source_type text,
  ADD COLUMN IF NOT EXISTS template_key text NOT NULL DEFAULT 'classic',
  ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

UPDATE resumes
SET source_type = CASE
    WHEN file_url IS NULL THEN 'builder'
    ELSE 'uploaded'
  END
WHERE source_type IS NULL;

ALTER TABLE resumes
  ALTER COLUMN source_type SET NOT NULL,
  ALTER COLUMN source_type SET DEFAULT 'uploaded';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'resumes_source_type_check'
  ) THEN
    ALTER TABLE resumes
      ADD CONSTRAINT resumes_source_type_check
      CHECK (source_type IN ('uploaded', 'builder'));
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS resumes_job_seeker_source_active_idx
  ON resumes(job_seeker_id, source_type)
  WHERE deleted_at IS NULL;
