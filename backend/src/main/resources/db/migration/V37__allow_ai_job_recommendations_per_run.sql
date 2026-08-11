ALTER TABLE ai_job_recommendations
  DROP CONSTRAINT IF EXISTS ai_job_recommendations_job_seeker_job_unique;

CREATE UNIQUE INDEX IF NOT EXISTS ai_job_recommendations_run_job_unique_idx
  ON ai_job_recommendations(run_id, job_id)
  WHERE run_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ai_job_recommendations_run_rank_unique_idx
  ON ai_job_recommendations(run_id, rank_position)
  WHERE run_id IS NOT NULL AND rank_position IS NOT NULL;

CREATE INDEX IF NOT EXISTS ai_job_recommendations_candidate_run_idx
  ON ai_job_recommendations(job_seeker_id, run_id)
  WHERE run_id IS NOT NULL;
