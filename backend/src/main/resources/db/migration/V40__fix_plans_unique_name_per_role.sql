-- Fix: remote DB may still have global unique on plans.name (plans_name_key)
-- because V29 was added after Flyway already passed that version.
-- Allow same plan name for different target_role (employer / job_seeker / all).

ALTER TABLE plans DROP CONSTRAINT IF EXISTS plans_name_key;
DROP INDEX IF EXISTS plans_name_key;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'plans_name_target_role_key'
  ) THEN
    ALTER TABLE plans
      ADD CONSTRAINT plans_name_target_role_key UNIQUE (name, target_role);
  END IF;
END $$;
