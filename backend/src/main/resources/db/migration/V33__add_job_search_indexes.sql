CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_jobs_title_trgm
    ON jobs USING gin (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_jobs_location_trgm
    ON jobs USING gin (location gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_companies_name_trgm
    ON companies USING gin (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_jobs_public_search_filters
    ON jobs (status, deadline, job_type, work_mode, experience_level);
