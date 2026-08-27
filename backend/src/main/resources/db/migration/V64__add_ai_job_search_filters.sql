ALTER TABLE ai_job_search_runs
    ADD COLUMN search_filters JSONB NOT NULL DEFAULT '{}'::jsonb;
