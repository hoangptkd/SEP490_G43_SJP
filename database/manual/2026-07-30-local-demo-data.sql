-- Local demo data for Smart Recruitment Portal.
-- Passwords:
--   admin@sjp.local / Admin@123
--   candidate.demo@sjp.local / Password123!
--   employer.demo@sjp.local / Password123!

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

WITH seed_users(id, email, raw_password, full_name, role, phone) AS (
  VALUES
    ('00000000-0000-0000-0000-000000000001'::uuid, 'admin@sjp.local', 'Admin@123', 'Local Admin', 'admin', '0900000001'),
    ('00000000-0000-0000-0000-000000000002'::uuid, 'candidate.demo@sjp.local', 'Password123!', 'Demo Candidate', 'job_seeker', '0900000002'),
    ('00000000-0000-0000-0000-000000000003'::uuid, 'employer.demo@sjp.local', 'Password123!', 'Demo Employer', 'employer', '0900000003')
)
INSERT INTO users (id, email, password_hash, full_name, role, phone, email_verified_at, status, created_at, updated_at)
SELECT id, email, crypt(raw_password, gen_salt('bf', 10)), full_name, role, phone, now(), 'active', now(), now()
FROM seed_users
ON CONFLICT (email) DO UPDATE SET
  password_hash = EXCLUDED.password_hash,
  full_name = EXCLUDED.full_name,
  role = EXCLUDED.role,
  phone = EXCLUDED.phone,
  email_verified_at = COALESCE(users.email_verified_at, now()),
  status = 'active',
  updated_at = now();

WITH seed_categories(id, name, slug, description) AS (
  VALUES
    ('10000000-0000-0000-0000-000000000001'::uuid, 'Software Engineering', 'software-engineering', 'Backend, frontend, fullstack, mobile, and platform roles.'),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'Data and AI', 'data-ai', 'Data engineering, analytics, machine learning, and AI roles.'),
    ('10000000-0000-0000-0000-000000000003'::uuid, 'Product and Design', 'product-design', 'Product management, UX/UI, and business analysis roles.'),
    ('10000000-0000-0000-0000-000000000004'::uuid, 'DevOps and Cloud', 'devops-cloud', 'Infrastructure, cloud, SRE, and CI/CD roles.')
)
INSERT INTO categories (id, name, slug, description, status, created_at, updated_at)
SELECT id, name, slug, description, 'active', now(), now()
FROM seed_categories
ON CONFLICT (slug) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  status = 'active',
  updated_at = now();

WITH seed_skills(id, name, slug, category, description) AS (
  VALUES
    ('20000000-0000-0000-0000-000000000001'::uuid, 'Java', 'java', 'Backend', 'Java application development.'),
    ('20000000-0000-0000-0000-000000000002'::uuid, 'Spring Boot', 'spring-boot', 'Backend', 'REST APIs and backend services with Spring Boot.'),
    ('20000000-0000-0000-0000-000000000003'::uuid, 'PostgreSQL', 'postgresql', 'Database', 'Relational schema design and SQL queries.'),
    ('20000000-0000-0000-0000-000000000004'::uuid, 'React', 'react', 'Frontend', 'User interface development with React.'),
    ('20000000-0000-0000-0000-000000000005'::uuid, 'TypeScript', 'typescript', 'Frontend', 'Typed JavaScript application development.'),
    ('20000000-0000-0000-0000-000000000006'::uuid, 'Docker', 'docker', 'DevOps', 'Containerized application delivery.')
)
INSERT INTO skills (id, name, slug, category, description, created_at)
SELECT id, name, slug, category, description, now()
FROM seed_skills
ON CONFLICT (slug) DO UPDATE SET
  name = EXCLUDED.name,
  category = EXCLUDED.category,
  description = EXCLUDED.description;

INSERT INTO companies (
  id, name, logo_url, description, website, industry, location, company_size, tax_code,
  status, verification_status, created_at, updated_at
)
VALUES (
  '30000000-0000-0000-0000-000000000001'::uuid,
  'SJP Demo Tech',
  'https://placehold.co/128x128?text=SJP',
  'Demo technology company for local development and testing.',
  'https://sjp.local',
  'Software Development',
  'Ha Noi',
  250,
  'SJP-DEMO-LOCAL-001',
  'active',
  'verified',
  now(),
  now()
)
ON CONFLICT (name) DO UPDATE SET
  logo_url = EXCLUDED.logo_url,
  description = EXCLUDED.description,
  website = EXCLUDED.website,
  industry = EXCLUDED.industry,
  location = EXCLUDED.location,
  company_size = EXCLUDED.company_size,
  tax_code = EXCLUDED.tax_code,
  status = 'active',
  verification_status = 'verified',
  updated_at = now();

INSERT INTO employers (id, user_id, company_id, position, verification_status, is_owner, created_at, updated_at)
VALUES (
  '31000000-0000-0000-0000-000000000001'::uuid,
  '00000000-0000-0000-0000-000000000003'::uuid,
  '30000000-0000-0000-0000-000000000001'::uuid,
  'Recruitment Manager',
  'verified',
  true,
  now(),
  now()
)
ON CONFLICT (user_id) DO UPDATE SET
  company_id = EXCLUDED.company_id,
  position = EXCLUDED.position,
  verification_status = 'verified',
  is_owner = true,
  updated_at = now();

INSERT INTO job_seekers (
  id, user_id, headline, summary, location, date_of_birth, gender,
  years_of_experience, experience_level, linkedin_url, portfolio_url, created_at, updated_at
)
VALUES (
  '32000000-0000-0000-0000-000000000001'::uuid,
  '00000000-0000-0000-0000-000000000002'::uuid,
  'Java/Spring Boot Developer',
  'Backend developer with Spring Boot, PostgreSQL, and React integration experience.',
  'Ha Noi',
  '2001-05-20',
  'prefer_not_to_say',
  3,
  'middle',
  'https://linkedin.com/in/candidate-demo',
  'https://github.com/candidate-demo',
  now(),
  now()
)
ON CONFLICT (user_id) DO UPDATE SET
  headline = EXCLUDED.headline,
  summary = EXCLUDED.summary,
  location = EXCLUDED.location,
  date_of_birth = EXCLUDED.date_of_birth,
  gender = EXCLUDED.gender,
  years_of_experience = EXCLUDED.years_of_experience,
  experience_level = EXCLUDED.experience_level,
  linkedin_url = EXCLUDED.linkedin_url,
  portfolio_url = EXCLUDED.portfolio_url,
  updated_at = now();

INSERT INTO company_locations (id, company_id, branch_name, address, city, district, country, is_headquarter, created_at, updated_at)
VALUES
  ('33000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000001'::uuid, 'Ha Noi Office', 'Hoa Lac Hi-Tech Park', 'Ha Noi', 'Thach That', 'Vietnam', true, now(), now()),
  ('33000000-0000-0000-0000-000000000002'::uuid, '30000000-0000-0000-0000-000000000001'::uuid, 'Da Nang Office', 'Hai Chau District', 'Da Nang', 'Hai Chau', 'Vietnam', false, now(), now())
ON CONFLICT (id) DO UPDATE SET
  branch_name = EXCLUDED.branch_name,
  address = EXCLUDED.address,
  city = EXCLUDED.city,
  district = EXCLUDED.district,
  country = EXCLUDED.country,
  is_headquarter = EXCLUDED.is_headquarter,
  updated_at = now();

CREATE TABLE IF NOT EXISTS company_industries (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
  category_id uuid NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
  is_primary boolean NOT NULL DEFAULT false,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now(),
  CONSTRAINT unique_company_category UNIQUE (company_id, category_id)
);

INSERT INTO company_industries (id, company_id, category_id, is_primary, created_at, updated_at)
VALUES
  ('34000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000001'::uuid, true, now(), now()),
  ('34000000-0000-0000-0000-000000000002'::uuid, '30000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000004'::uuid, false, now(), now())
ON CONFLICT (company_id, category_id) DO UPDATE SET
  is_primary = EXCLUDED.is_primary,
  updated_at = now();

INSERT INTO company_documents (id, company_id, file_name, file_url, file_type, public_id, status, uploaded_at, reviewed_at, reviewed_by)
VALUES (
  '35000000-0000-0000-0000-000000000001'::uuid,
  '30000000-0000-0000-0000-000000000001'::uuid,
  'business-license-demo.pdf',
  '/uploads/company-documents/business-license-demo.pdf',
  'application/pdf',
  'local/company-documents/business-license-demo',
  'approved',
  now(),
  now(),
  '00000000-0000-0000-0000-000000000001'::uuid
)
ON CONFLICT (id) DO UPDATE SET
  file_name = EXCLUDED.file_name,
  file_url = EXCLUDED.file_url,
  file_type = EXCLUDED.file_type,
  public_id = EXCLUDED.public_id,
  status = EXCLUDED.status,
  reviewed_at = EXCLUDED.reviewed_at,
  reviewed_by = EXCLUDED.reviewed_by;

WITH seed_jobs(id, title, category_id, company_location_id, description, requirements, benefits, location, job_type, work_mode, salary_min, salary_max, experience_level, deadline, views_count) AS (
  VALUES
    ('40000000-0000-0000-0000-000000000001'::uuid, 'Java Backend Developer', '10000000-0000-0000-0000-000000000001'::uuid, '33000000-0000-0000-0000-000000000001'::uuid, 'Build REST APIs and backend services for recruitment workflows.', 'Java 17
Spring Boot
PostgreSQL
REST API design', 'Health insurance, annual leave, technical training, laptop provided.', 'Ha Noi', 'full_time', 'hybrid', 18000000, 30000000, 'junior', current_date + 45, 32),
    ('40000000-0000-0000-0000-000000000002'::uuid, 'Frontend Engineer React', '10000000-0000-0000-0000-000000000001'::uuid, '33000000-0000-0000-0000-000000000002'::uuid, 'Develop candidate and employer interfaces with React and TypeScript.', 'React
TypeScript
API integration
Component design', 'Remote-friendly policy, mentoring, performance bonus.', 'Da Nang', 'full_time', 'remote', 16000000, 28000000, 'junior', current_date + 40, 21),
    ('40000000-0000-0000-0000-000000000003'::uuid, 'Cloud Platform Engineer', '10000000-0000-0000-0000-000000000004'::uuid, '33000000-0000-0000-0000-000000000001'::uuid, 'Operate cloud infrastructure and deployment pipelines.', 'Docker
CI/CD
Linux
Monitoring', 'Cloud certification budget, flexible working time.', 'Ha Noi', 'full_time', 'hybrid', 30000000, 50000000, 'middle', current_date + 60, 15)
)
INSERT INTO jobs (
  id, company_id, category_id, created_by_employer_id, title, description, requirements,
  benefits, vacancies, working_time, salary_type, location, company_location_id, job_type,
  work_mode, salary_min, salary_max, currency, experience_level, status, posted_at,
  published_at, deadline, views_count, created_at, updated_at
)
SELECT
  id,
  '30000000-0000-0000-0000-000000000001'::uuid,
  category_id,
  '31000000-0000-0000-0000-000000000001'::uuid,
  title,
  description,
  requirements,
  benefits,
  2,
  '08:30 - 17:30, Monday to Friday',
  'range',
  location,
  company_location_id,
  job_type,
  work_mode,
  salary_min,
  salary_max,
  'VND',
  experience_level,
  'published',
  now() - interval '3 days',
  now() - interval '3 days',
  deadline,
  views_count,
  now(),
  now()
FROM seed_jobs
ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title,
  description = EXCLUDED.description,
  requirements = EXCLUDED.requirements,
  benefits = EXCLUDED.benefits,
  vacancies = EXCLUDED.vacancies,
  working_time = EXCLUDED.working_time,
  salary_type = EXCLUDED.salary_type,
  location = EXCLUDED.location,
  company_location_id = EXCLUDED.company_location_id,
  job_type = EXCLUDED.job_type,
  work_mode = EXCLUDED.work_mode,
  salary_min = EXCLUDED.salary_min,
  salary_max = EXCLUDED.salary_max,
  experience_level = EXCLUDED.experience_level,
  status = 'published',
  deadline = EXCLUDED.deadline,
  views_count = EXCLUDED.views_count,
  updated_at = now();

INSERT INTO job_skills (job_id, skill_id, is_required)
VALUES
  ('40000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000001'::uuid, true),
  ('40000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000002'::uuid, true),
  ('40000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000003'::uuid, true),
  ('40000000-0000-0000-0000-000000000002'::uuid, '20000000-0000-0000-0000-000000000004'::uuid, true),
  ('40000000-0000-0000-0000-000000000002'::uuid, '20000000-0000-0000-0000-000000000005'::uuid, true),
  ('40000000-0000-0000-0000-000000000003'::uuid, '20000000-0000-0000-0000-000000000006'::uuid, true)
ON CONFLICT (job_id, skill_id) DO UPDATE SET
  is_required = EXCLUDED.is_required;

INSERT INTO candidate_skills (job_seeker_id, skill_id, level, years_experience)
VALUES
  ('32000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000001'::uuid, 'advanced', 3),
  ('32000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000002'::uuid, 'advanced', 3),
  ('32000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000003'::uuid, 'intermediate', 2),
  ('32000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000004'::uuid, 'intermediate', 1)
ON CONFLICT (job_seeker_id, skill_id) DO UPDATE SET
  level = EXCLUDED.level,
  years_experience = EXCLUDED.years_experience;

INSERT INTO resumes (
  id, job_seeker_id, title, content_json, file_url, file_name, file_type, file_size,
  parsed_text, parse_status, is_primary, created_at, updated_at
)
VALUES (
  '50000000-0000-0000-0000-000000000001'::uuid,
  '32000000-0000-0000-0000-000000000001'::uuid,
  'Demo Java Backend CV',
  '{"summary":"Java backend developer","skills":["Java","Spring Boot","PostgreSQL","React"]}'::jsonb,
  '/uploads/cvs/demo-java-backend-cv.pdf',
  'demo-java-backend-cv.pdf',
  'application/pdf',
  245760,
  'Java backend developer with Spring Boot, PostgreSQL, REST API, and React integration experience.',
  'parsed',
  true,
  now(),
  now()
)
ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title,
  content_json = EXCLUDED.content_json,
  file_url = EXCLUDED.file_url,
  file_name = EXCLUDED.file_name,
  file_type = EXCLUDED.file_type,
  file_size = EXCLUDED.file_size,
  parsed_text = EXCLUDED.parsed_text,
  parse_status = EXCLUDED.parse_status,
  is_primary = true,
  updated_at = now();

INSERT INTO applications (
  id, job_id, job_seeker_id, resume_id, cover_letter, status, ai_match_score,
  ai_match_analysis, resume_snapshot_json, resume_file_url_snapshot, applied_at,
  reviewed_at, status_updated_by, updated_at
)
VALUES (
  '60000000-0000-0000-0000-000000000001'::uuid,
  '40000000-0000-0000-0000-000000000001'::uuid,
  '32000000-0000-0000-0000-000000000001'::uuid,
  '50000000-0000-0000-0000-000000000001'::uuid,
  'I am interested in building reliable recruitment systems with Java and Spring Boot.',
  'reviewed',
  86,
  'Strong match for Java, Spring Boot, PostgreSQL, and REST API requirements.',
  '{"title":"Demo Java Backend CV","skills":["Java","Spring Boot","PostgreSQL"]}'::jsonb,
  '/uploads/cvs/demo-java-backend-cv.pdf',
  now() - interval '2 days',
  now() - interval '1 day',
  '00000000-0000-0000-0000-000000000003'::uuid,
  now()
)
ON CONFLICT (job_id, job_seeker_id) DO UPDATE SET
  resume_id = EXCLUDED.resume_id,
  cover_letter = EXCLUDED.cover_letter,
  status = EXCLUDED.status,
  ai_match_score = EXCLUDED.ai_match_score,
  ai_match_analysis = EXCLUDED.ai_match_analysis,
  resume_snapshot_json = EXCLUDED.resume_snapshot_json,
  resume_file_url_snapshot = EXCLUDED.resume_file_url_snapshot,
  reviewed_at = EXCLUDED.reviewed_at,
  status_updated_by = EXCLUDED.status_updated_by,
  updated_at = now();

INSERT INTO application_status_history (id, application_id, old_status, new_status, changed_by_user_id, note, created_at)
VALUES
  ('61000000-0000-0000-0000-000000000001'::uuid, '60000000-0000-0000-0000-000000000001'::uuid, NULL, 'applied', '00000000-0000-0000-0000-000000000002'::uuid, 'Demo application submitted.', now() - interval '2 days'),
  ('61000000-0000-0000-0000-000000000002'::uuid, '60000000-0000-0000-0000-000000000001'::uuid, 'applied', 'reviewed', '00000000-0000-0000-0000-000000000003'::uuid, 'Employer reviewed the demo application.', now() - interval '1 day')
ON CONFLICT (id) DO NOTHING;

INSERT INTO saved_jobs (job_seeker_id, job_id, saved_at)
VALUES
  ('32000000-0000-0000-0000-000000000001'::uuid, '40000000-0000-0000-0000-000000000002'::uuid, now()),
  ('32000000-0000-0000-0000-000000000001'::uuid, '40000000-0000-0000-0000-000000000003'::uuid, now())
ON CONFLICT (job_seeker_id, job_id) DO NOTHING;

INSERT INTO plans (id, name, target_role, description, price, currency, duration_days, features, status, sort_order, created_at, updated_at)
VALUES
  ('70000000-0000-0000-0000-000000000001'::uuid, 'Candidate Free', 'job_seeker', 'Basic candidate plan for local testing.', 0, 'VND', 30, '{"benefits":["Create profile","Upload CV","Apply to jobs"]}'::jsonb, 'active', 1, now(), now()),
  ('70000000-0000-0000-0000-000000000002'::uuid, 'Employer Starter', 'employer', 'Starter employer plan for local testing.', 199000, 'VND', 30, '{"benefits":["Post jobs","Review applications","Company profile"]}'::jsonb, 'active', 2, now(), now())
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  target_role = EXCLUDED.target_role,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  currency = EXCLUDED.currency,
  duration_days = EXCLUDED.duration_days,
  features = EXCLUDED.features,
  status = EXCLUDED.status,
  sort_order = EXCLUDED.sort_order,
  updated_at = now();

INSERT INTO subscriptions (id, user_id, plan_id, status, start_date, end_date, created_at, updated_at)
VALUES
  ('71000000-0000-0000-0000-000000000001'::uuid, '00000000-0000-0000-0000-000000000002'::uuid, '70000000-0000-0000-0000-000000000001'::uuid, 'active', now(), now() + interval '30 days', now(), now()),
  ('71000000-0000-0000-0000-000000000002'::uuid, '00000000-0000-0000-0000-000000000003'::uuid, '70000000-0000-0000-0000-000000000002'::uuid, 'active', now(), now() + interval '30 days', now(), now())
ON CONFLICT (id) DO UPDATE SET
  status = EXCLUDED.status,
  end_date = EXCLUDED.end_date,
  updated_at = now();

INSERT INTO notifications (id, user_id, title, message, type, ref_id, ref_type, is_read, link_url, channel, created_at)
VALUES
  ('80000000-0000-0000-0000-000000000001'::uuid, '00000000-0000-0000-0000-000000000002'::uuid, 'Welcome to Smart Recruitment', 'Your local demo candidate account is ready.', 'WELCOME', NULL, NULL, false, '/candidate', 'in_app', now()),
  ('80000000-0000-0000-0000-000000000002'::uuid, '00000000-0000-0000-0000-000000000003'::uuid, 'Company verified', 'SJP Demo Tech is verified for local testing.', 'COMPANY_VERIFIED', '30000000-0000-0000-0000-000000000001', 'COMPANY', false, '/employer/company', 'in_app', now())
ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title,
  message = EXCLUDED.message,
  is_read = EXCLUDED.is_read,
  link_url = EXCLUDED.link_url,
  channel = EXCLUDED.channel;

INSERT INTO job_review_history (id, job_id, reviewed_by, action, reason, reviewed_at)
VALUES
  ('81000000-0000-0000-0000-000000000001'::uuid, '40000000-0000-0000-0000-000000000001'::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'APPROVED', 'Approved for local demo data.', now() - interval '3 days'),
  ('81000000-0000-0000-0000-000000000002'::uuid, '40000000-0000-0000-0000-000000000002'::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'APPROVED', 'Approved for local demo data.', now() - interval '3 days')
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS job_reports (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
  reporter_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  reason text NOT NULL,
  description text,
  status text NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'dismissed', 'resolved', 'awaiting_company', 'resubmitted')),
  admin_note text,
  resolved_by uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  resolved_at timestamptz,
  reporter_full_name text,
  reporter_phone text,
  reporter_date_of_birth date
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_job_reports_pending_per_user
  ON job_reports (job_id, reporter_user_id)
  WHERE status = 'pending';

INSERT INTO job_reports (
  id, job_id, reporter_user_id, reason, description, status, admin_note, created_at,
  reporter_full_name, reporter_phone, reporter_date_of_birth
)
VALUES (
  '82000000-0000-0000-0000-000000000001'::uuid,
  '40000000-0000-0000-0000-000000000003'::uuid,
  '00000000-0000-0000-0000-000000000002'::uuid,
  'suspicious_content',
  'Demo pending report for admin review workflow.',
  'pending',
  NULL,
  now(),
  'Demo Candidate',
  '0900000002',
  '2001-05-20'
)
ON CONFLICT DO NOTHING;

WITH seed_session AS (
  INSERT INTO interview_sessions (
    id, job_seeker_id, application_id, job_id, context_type, practice_context_json,
    title, session_type, status, total_questions, overall_score, ai_summary,
    started_at, completed_at, created_at, updated_at
  )
  VALUES (
    '90000000-0000-0000-0000-000000000001'::uuid,
    '32000000-0000-0000-0000-000000000001'::uuid,
    '60000000-0000-0000-0000-000000000001'::uuid,
    '40000000-0000-0000-0000-000000000001'::uuid,
    'application',
    '{}'::jsonb,
    'Java Backend Developer Demo Interview',
    'job_based',
    'completed',
    2,
    78,
    'Good understanding of backend fundamentals and clear motivation.',
    now() - interval '1 day',
    now() - interval '1 day' + interval '15 minutes',
    now() - interval '1 day',
    now()
  )
  ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    overall_score = EXCLUDED.overall_score,
    ai_summary = EXCLUDED.ai_summary,
    updated_at = now()
  RETURNING id
)
INSERT INTO interview_questions (id, session_id, order_index, question_type, content, difficulty, skill_tag, time_limit_seconds, ai_generated)
VALUES
  ('91000000-0000-0000-0000-000000000001'::uuid, '90000000-0000-0000-0000-000000000001'::uuid, 1, 'general', 'Tell us about your backend experience and the project you are most proud of.', 'easy', 'Java Backend', 180, true),
  ('91000000-0000-0000-0000-000000000002'::uuid, '90000000-0000-0000-0000-000000000001'::uuid, 2, 'technical', 'How would you design transaction handling for a multi-step Spring Boot workflow?', 'medium', 'Spring Boot', 180, true)
ON CONFLICT (session_id, order_index) DO UPDATE SET
  content = EXCLUDED.content,
  question_type = EXCLUDED.question_type,
  difficulty = EXCLUDED.difficulty,
  skill_tag = EXCLUDED.skill_tag,
  time_limit_seconds = EXCLUDED.time_limit_seconds,
  ai_generated = EXCLUDED.ai_generated;

INSERT INTO interview_answers (
  id, question_id, session_id, transcript_text, duration_seconds, is_skipped,
  answered_at, transcript_status, feedback_status, evaluation_source, evaluation_fallback
)
VALUES
  ('92000000-0000-0000-0000-000000000001'::uuid, '91000000-0000-0000-0000-000000000001'::uuid, '90000000-0000-0000-0000-000000000001'::uuid, 'I have three years of backend experience with Java, Spring Boot, and PostgreSQL.', 85, false, now() - interval '1 day', 'completed', 'completed', 'fallback', true),
  ('92000000-0000-0000-0000-000000000002'::uuid, '91000000-0000-0000-0000-000000000002'::uuid, '90000000-0000-0000-0000-000000000001'::uuid, 'I would keep all related writes in one service transaction and use clear rollback rules.', 95, false, now() - interval '1 day', 'completed', 'completed', 'fallback', true)
ON CONFLICT (session_id, question_id) DO UPDATE SET
  transcript_text = EXCLUDED.transcript_text,
  duration_seconds = EXCLUDED.duration_seconds,
  is_skipped = EXCLUDED.is_skipped,
  answered_at = EXCLUDED.answered_at,
  transcript_status = EXCLUDED.transcript_status,
  feedback_status = EXCLUDED.feedback_status,
  evaluation_source = EXCLUDED.evaluation_source,
  evaluation_fallback = EXCLUDED.evaluation_fallback;

INSERT INTO ai_answer_feedbacks (
  id, answer_id, relevance_score, clarity_score, depth_score, confidence_score,
  overall_score, strengths, weaknesses, suggestions, feedback, model_used, generated_at
)
VALUES
  ('93000000-0000-0000-0000-000000000001'::uuid, '92000000-0000-0000-0000-000000000001'::uuid, 80, 78, 74, 76, 77, 'Relevant backend experience.', 'Could add measurable results.', 'Use a STAR structure with impact metrics.', 'Solid answer for a junior to middle backend role.', 'local-seed', now()),
  ('93000000-0000-0000-0000-000000000002'::uuid, '92000000-0000-0000-0000-000000000002'::uuid, 82, 80, 78, 75, 79, 'Shows awareness of transaction boundaries.', 'Could mention isolation and retries.', 'Add an example with failure handling.', 'Good technical answer with room for more detail.', 'local-seed', now())
ON CONFLICT (id) DO UPDATE SET
  relevance_score = EXCLUDED.relevance_score,
  clarity_score = EXCLUDED.clarity_score,
  depth_score = EXCLUDED.depth_score,
  confidence_score = EXCLUDED.confidence_score,
  overall_score = EXCLUDED.overall_score,
  strengths = EXCLUDED.strengths,
  weaknesses = EXCLUDED.weaknesses,
  suggestions = EXCLUDED.suggestions,
  feedback = EXCLUDED.feedback,
  model_used = EXCLUDED.model_used,
  generated_at = EXCLUDED.generated_at;

INSERT INTO ai_session_feedbacks (
  id, session_id, overall_score, ai_summary, strengths, weaknesses, suggestions,
  model_used, generated_at, evaluation_source, evaluation_fallback
)
VALUES (
  '94000000-0000-0000-0000-000000000001'::uuid,
  '90000000-0000-0000-0000-000000000001'::uuid,
  78,
  'Candidate communicates backend experience clearly and understands common Spring Boot concerns.',
  'Clear motivation, relevant Java and database experience.',
  'Needs deeper detail on scaling and transaction edge cases.',
  'Prepare examples with metrics, tradeoffs, and production incidents.',
  'local-seed',
  now(),
  'fallback',
  true
)
ON CONFLICT (id) DO UPDATE SET
  overall_score = EXCLUDED.overall_score,
  ai_summary = EXCLUDED.ai_summary,
  strengths = EXCLUDED.strengths,
  weaknesses = EXCLUDED.weaknesses,
  suggestions = EXCLUDED.suggestions,
  model_used = EXCLUDED.model_used,
  generated_at = EXCLUDED.generated_at,
  evaluation_source = EXCLUDED.evaluation_source,
  evaluation_fallback = EXCLUDED.evaluation_fallback;
