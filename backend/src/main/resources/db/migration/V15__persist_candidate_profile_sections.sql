ALTER TABLE job_seekers
  ADD COLUMN IF NOT EXISTS education_json jsonb NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN IF NOT EXISTS work_experience_json jsonb NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN IF NOT EXISTS projects_json jsonb NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN IF NOT EXISTS certifications_json jsonb NOT NULL DEFAULT '[]'::jsonb;

