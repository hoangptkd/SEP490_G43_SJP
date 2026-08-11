CREATE TABLE ai_job_search_runs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_seeker_id uuid NOT NULL REFERENCES job_seekers(id) ON DELETE CASCADE,
  status text NOT NULL CHECK (status IN ('PROCESSING', 'SUCCEEDED', 'FAILED')),
  input_hash varchar(64) NOT NULL,
  profile_updated_at timestamptz,
  cv_id uuid REFERENCES resumes(id) ON DELETE SET NULL,
  cv_type text CHECK (cv_type IS NULL OR cv_type IN ('UPLOADED', 'BUILDER')),
  cv_updated_at timestamptz,
  model_used text,
  prompt_version text NOT NULL,
  result_count integer NOT NULL DEFAULT 0 CHECK (result_count >= 0 AND result_count <= 10),
  quota_consumed boolean NOT NULL DEFAULT false,
  failure_code text,
  started_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  expires_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ai_job_search_runs_one_processing_per_candidate_idx
  ON ai_job_search_runs(job_seeker_id)
  WHERE status = 'PROCESSING';

CREATE INDEX ai_job_search_runs_candidate_created_idx
  ON ai_job_search_runs(job_seeker_id, created_at DESC);

CREATE INDEX ai_job_search_runs_cleanup_idx
  ON ai_job_search_runs(created_at);

CREATE INDEX ai_job_search_runs_cv_id_idx
  ON ai_job_search_runs(cv_id)
  WHERE cv_id IS NOT NULL;

CREATE TABLE candidate_ai_consents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_seeker_id uuid NOT NULL REFERENCES job_seekers(id) ON DELETE CASCADE,
  purpose text NOT NULL CHECK (purpose = 'AI_JOB_SEARCH'),
  policy_version text NOT NULL,
  granted_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX candidate_ai_consents_one_active_idx
  ON candidate_ai_consents(job_seeker_id, purpose)
  WHERE revoked_at IS NULL;

CREATE INDEX candidate_ai_consents_candidate_idx
  ON candidate_ai_consents(job_seeker_id, created_at DESC);

ALTER TABLE ai_job_recommendations
  ADD COLUMN run_id uuid REFERENCES ai_job_search_runs(id) ON DELETE CASCADE,
  ADD COLUMN rank_position integer CHECK (rank_position IS NULL OR rank_position BETWEEN 1 AND 10);

CREATE INDEX ai_job_recommendations_run_id_idx
  ON ai_job_recommendations(run_id)
  WHERE run_id IS NOT NULL;

INSERT INTO system_settings (setting_key, setting_value, description)
VALUES
  ('ai_job_search_enabled', 'true', 'Cho phép ứng viên tìm việc phù hợp bằng AI'),
  ('ai_job_search_policy_version', 'ai-job-search-v1', 'Phiên bản chính sách đồng ý cho AI Job Search'),
  ('max_ai_job_searches_per_month', '3', 'Số lượt AI Job Search miễn phí mỗi tháng')
ON CONFLICT (setting_key) DO NOTHING;
