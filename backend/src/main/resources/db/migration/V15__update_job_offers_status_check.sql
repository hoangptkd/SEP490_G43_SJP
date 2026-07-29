ALTER TABLE job_offers DROP CONSTRAINT IF EXISTS job_offers_status_check;
ALTER TABLE job_offers ADD CONSTRAINT job_offers_status_check CHECK (status IN ('sent', 'accepted', 'rejected', 'expired', 'employer_declined_negotiation', 'withdrawn_by_candidate'));
