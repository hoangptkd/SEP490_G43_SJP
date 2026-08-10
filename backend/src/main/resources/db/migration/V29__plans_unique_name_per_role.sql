-- Cho phép cùng tên Plus/Pro/Premium cho các đối tượng khác nhau (employer / job_seeker / all)
ALTER TABLE plans DROP CONSTRAINT IF EXISTS plans_name_key;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'plans_name_target_role_key'
  ) THEN
    ALTER TABLE plans
      ADD CONSTRAINT plans_name_target_role_key UNIQUE (name, target_role);
  END IF;
END $$;
