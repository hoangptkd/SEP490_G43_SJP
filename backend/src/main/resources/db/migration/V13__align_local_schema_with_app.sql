CREATE TABLE IF NOT EXISTS company_locations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
  branch_name text NOT NULL,
  address text,
  city text,
  district text,
  country text NOT NULL DEFAULT 'Vietnam',
  is_headquarter boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS company_locations_company_id_idx
  ON company_locations(company_id);

DROP TRIGGER IF EXISTS company_locations_set_updated_at ON company_locations;
CREATE TRIGGER company_locations_set_updated_at
BEFORE UPDATE ON company_locations
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE jobs
  ADD COLUMN IF NOT EXISTS company_location_id uuid REFERENCES company_locations(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS benefits text,
  ADD COLUMN IF NOT EXISTS vacancies integer NOT NULL DEFAULT 1 CHECK (vacancies > 0),
  ADD COLUMN IF NOT EXISTS working_time text,
  ADD COLUMN IF NOT EXISTS salary_type text NOT NULL DEFAULT 'range';

CREATE INDEX IF NOT EXISTS jobs_company_location_id_idx
  ON jobs(company_location_id);

CREATE TABLE IF NOT EXISTS company_documents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
  file_name text NOT NULL,
  file_url text NOT NULL,
  file_type text NOT NULL,
  public_id text,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
  reject_reason text,
  uploaded_at timestamptz NOT NULL DEFAULT now(),
  reviewed_at timestamptz,
  reviewed_by uuid REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS company_documents_company_id_idx
  ON company_documents(company_id);

CREATE INDEX IF NOT EXISTS company_documents_status_idx
  ON company_documents(status);

CREATE TABLE IF NOT EXISTS job_review_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
  reviewed_by uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  action varchar(20) NOT NULL CHECK (action IN ('APPROVED', 'REJECTED')),
  reason text,
  reviewed_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS job_review_history_job_id_idx
  ON job_review_history(job_id);

CREATE INDEX IF NOT EXISTS job_review_history_reviewed_by_idx
  ON job_review_history(reviewed_by);
