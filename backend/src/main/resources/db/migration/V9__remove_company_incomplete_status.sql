-- Remove incomplete status; map existing rows back to rejected
UPDATE companies
SET verification_status = 'rejected'
WHERE LOWER(verification_status) = 'incomplete';

UPDATE employers
SET verification_status = 'rejected'
WHERE LOWER(verification_status) = 'incomplete';

ALTER TABLE employers DROP CONSTRAINT IF EXISTS employers_verification_status_check;
ALTER TABLE employers
  ADD CONSTRAINT employers_verification_status_check
  CHECK (verification_status IN ('pending', 'verified', 'rejected'));
