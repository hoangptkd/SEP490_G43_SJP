CREATE INDEX IF NOT EXISTS jobs_published_created_at_idx
    ON jobs (created_at DESC)
    WHERE status = 'published';
