DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'job_offers' AND relkind = 'r') THEN
		ALTER TABLE job_offers DROP CONSTRAINT IF EXISTS job_offers_status_check;
		ALTER TABLE job_offers ADD CONSTRAINT job_offers_status_check CHECK (status IN ('sent', 'accepted', 'rejected', 'expired', 'employer_declined_negotiation', 'withdrawn_by_candidate'));
	END IF;
END$$ LANGUAGE plpgsql;
