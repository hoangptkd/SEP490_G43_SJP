-- Allow company/employer verification status: incomplete (chưa đủ điều kiện)
ALTER TABLE employers DROP CONSTRAINT IF EXISTS employers_verification_status_check;
ALTER TABLE employers
  ADD CONSTRAINT employers_verification_status_check
  CHECK (verification_status IN ('pending', 'verified', 'rejected', 'incomplete'));

-- companies.verification_status has no enum check in V1; keep free-form but normalize known values in app.
-- Optional: ensure column exists for older DBs
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'companies' AND column_name = 'verification_status'
  ) THEN
    ALTER TABLE companies ADD COLUMN verification_status text NOT NULL DEFAULT 'pending';
  END IF;
END $$;
