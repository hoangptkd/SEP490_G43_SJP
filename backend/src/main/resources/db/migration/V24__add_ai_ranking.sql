-- Add ranking_config to jobs
ALTER TABLE jobs
ADD COLUMN IF NOT EXISTS ranking_config jsonb DEFAULT '{
  "template": "balanced",
  "weights": {
    "skills": 30,
    "experience": 30,
    "projects": 15,
    "education": 15,
    "certificates": 10
  },
  "enabled_criteria": ["skills", "experience", "projects", "education", "certificates"],
  "mandatory": {
    "skills": [],
    "certificates": [],
    "min_experience_years": null,
    "education_level": null
  }
}'::jsonb;

-- Add AI ranking result extension columns
ALTER TABLE ai_ranking_results
ADD COLUMN IF NOT EXISTS missing_requirements jsonb DEFAULT '[]'::jsonb,
ADD COLUMN IF NOT EXISTS model_used text,
ADD COLUMN IF NOT EXISTS ranked_at timestamptz DEFAULT now();

-- Add need_rerank flag to applications and resumes
ALTER TABLE applications
ADD COLUMN IF NOT EXISTS need_rerank boolean DEFAULT false;

ALTER TABLE resumes
ADD COLUMN IF NOT EXISTS need_rerank boolean DEFAULT false;

-- Add indices for optimization
CREATE INDEX IF NOT EXISTS idx_jobs_ranking_config 
ON jobs USING gin (ranking_config);

CREATE INDEX IF NOT EXISTS idx_ai_ranking_results_missing 
ON ai_ranking_results USING gin (missing_requirements);

CREATE INDEX IF NOT EXISTS idx_applications_need_rerank 
ON applications (need_rerank) 
WHERE need_rerank = true;

-- Modify ai_ranking_results to allow per-application ranking without a batch job
ALTER TABLE ai_ranking_results 
ALTER COLUMN ranking_job_id DROP NOT NULL;

ALTER TABLE ai_ranking_results 
DROP CONSTRAINT IF EXISTS ai_ranking_results_ranking_application_unique;

ALTER TABLE ai_ranking_results 
ADD CONSTRAINT ai_ranking_results_application_unique UNIQUE (application_id);
