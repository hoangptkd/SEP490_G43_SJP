-- Smart Job Portal - PostgreSQL schema generated from physical_diagram.drawio
-- Final revised version for Smart Recruitment Portal
-- Fixes included:
-- - Remove duplicated user active/verified flags; use status and email_verified_at
-- - Composite FK to ensure employer creates jobs only for their own company
-- - Triggers to prevent wrong resume/application and wrong AI ranking/application links
-- - Composite FK to keep interview answers aligned with their question/session
-- - Partial unique index for one primary resume per candidate
-- - Search indexes for job listing filters
-- - OAuth, email verification, password reset, application history, notes, quotas, webhooks, audit logs
    -- tk mt admin: admin@sjp.local/ Admin@123
create extension if not exists pgcrypto;
create extension if not exists citext;

create or replace function set_updated_at()
returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

create table users (
  id uuid primary key default gen_random_uuid(),
  email citext not null unique,
  password_hash text,
  full_name text,
  role text not null check (role in ('job_seeker', 'employer', 'admin')),
  phone text,
  avatar_url text,
  email_verified_at timestamptz,
  last_login_at timestamptz,
  status text not null default 'active' check (status in ('active', 'inactive', 'suspended', 'deleted')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table oauth_accounts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  provider text not null,
  provider_user_id text not null,
  provider_email citext,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint oauth_accounts_provider_user_unique unique (provider, provider_user_id)
);

create table email_verification_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  token_hash text not null unique,
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now()
);

create table password_reset_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  token_hash text not null unique,
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now()
);

create table companies (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  logo_url text,
  description text,
  website text,
  industry text,
  location text,
  company_size integer check (company_size is null or company_size >= 0),
  tax_code text unique,
  verification_status text not null default 'unverified' check (verification_status in ('unverified', 'pending', 'verified', 'rejected')),
  status text not null default 'pending' check (status in ('pending', 'active', 'rejected', 'suspended')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table company_documents (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  file_name text not null,
  file_url text not null,
  file_type text not null,
  public_id text,
  status text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
  reject_reason text,
  uploaded_at timestamptz not null default now(),
  reviewed_at timestamptz,
  reviewed_by uuid references users(id) on delete set null
);

create table job_seekers (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references users(id) on delete cascade,
  headline text,
  summary text,
  location text,
  date_of_birth date,
  gender text check (gender is null or gender in ('male', 'female', 'other', 'prefer_not_to_say')),
  years_of_experience integer not null default 0 check (years_of_experience >= 0),
  experience_level text check (experience_level is null or experience_level in ('intern', 'fresher', 'junior', 'middle', 'senior', 'lead', 'manager')),
  linkedin_url text,
  portfolio_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table employers (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references users(id) on delete cascade,
  company_id uuid not null references companies(id) on delete cascade,
  position text,
  verification_status text not null default 'pending' check (verification_status in ('pending', 'verified', 'rejected')),
  is_owner boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint employers_id_company_unique unique (id, company_id)
);

create table categories (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  slug text not null unique,
  parent_id uuid references categories(id) on delete set null,
  description text,
  status text not null default 'active' check (status in ('active', 'inactive')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table jobs (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  category_id uuid references categories(id) on delete set null,
  created_by_employer_id uuid not null,
  reviewed_by_user_id uuid references users(id) on delete set null,
  title text not null,
  description text not null,
  requirements text,
  location text,
  job_type text check (job_type is null or job_type in ('full_time', 'part_time', 'contract', 'internship', 'freelance')),
  work_mode text check (work_mode is null or work_mode in ('onsite', 'remote', 'hybrid')),
  salary_min numeric(14,2) check (salary_min is null or salary_min >= 0),
  salary_max numeric(14,2) check (salary_max is null or salary_max >= 0),
  currency text not null default 'VND',
  experience_level text check (experience_level is null or experience_level in ('intern', 'fresher', 'junior', 'middle', 'senior', 'lead', 'manager')),
  status text not null default 'draft' check (status in ('draft', 'pending_review', 'published', 'rejected', 'closed', 'expired')),
  rejection_reason text,
  posted_at timestamptz,
  published_at timestamptz,
  closed_at timestamptz,
  deadline date,
  views_count integer not null default 0 check (views_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint jobs_salary_range_check check (
    salary_min is null or salary_max is null or salary_min <= salary_max
  ),
  constraint jobs_creator_company_fk foreign key (created_by_employer_id, company_id)
    references employers(id, company_id) on delete restrict
);

create table resumes (
  id uuid primary key default gen_random_uuid(),
  job_seeker_id uuid not null references job_seekers(id) on delete cascade,
  title text not null,
  content_json jsonb not null default '{}'::jsonb,
  file_url text,
  file_name text,
  file_type text,
  file_size bigint check (file_size is null or file_size >= 0),
  parsed_text text,
  parse_status text not null default 'pending' check (parse_status in ('pending', 'parsed', 'failed')),
  is_primary boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table applications (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references jobs(id) on delete cascade,
  job_seeker_id uuid not null references job_seekers(id) on delete cascade,
  resume_id uuid references resumes(id) on delete set null,
  cover_letter text,
  status text not null default 'applied' check (
    status in ('applied', 'reviewed', 'shortlisted', 'interview_scheduled', 'accepted', 'rejected', 'withdrawn')
  ),
  ai_match_score integer check (ai_match_score is null or ai_match_score between 0 and 100),
  ai_match_analysis text,
  resume_snapshot_json jsonb,
  resume_file_url_snapshot text,
  applied_at timestamptz not null default now(),
  reviewed_at timestamptz,
  status_updated_by uuid references users(id) on delete set null,
  updated_at timestamptz not null default now(),
  constraint applications_job_seeker_unique unique (job_id, job_seeker_id)
);

create table application_status_history (
  id uuid primary key default gen_random_uuid(),
  application_id uuid not null references applications(id) on delete cascade,
  old_status text,
  new_status text not null,
  changed_by_user_id uuid references users(id) on delete set null,
  note text,
  created_at timestamptz not null default now()
);

create table application_internal_notes (
  id uuid primary key default gen_random_uuid(),
  application_id uuid not null references applications(id) on delete cascade,
  employer_id uuid not null references employers(id) on delete cascade,
  note text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table interview_schedules (
  id uuid primary key default gen_random_uuid(),
  application_id uuid not null references applications(id) on delete cascade,
  employer_id uuid not null references employers(id) on delete restrict,
  job_seeker_id uuid not null references job_seekers(id) on delete cascade,
  round_number integer not null default 1 check (round_number > 0),
  scheduled_at timestamptz not null,
  meeting_link text,
  location text,
  status text not null default 'scheduled' check (status in ('scheduled', 'rescheduled', 'completed', 'cancelled', 'no_show')),
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint interview_schedules_application_round_unique unique (application_id, round_number)
);

create table saved_jobs (
  id uuid primary key default gen_random_uuid(),
  job_seeker_id uuid not null references job_seekers(id) on delete cascade,
  job_id uuid not null references jobs(id) on delete cascade,
  saved_at timestamptz not null default now(),
  constraint saved_jobs_job_seeker_job_unique unique (job_seeker_id, job_id)
);

create table skills (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  slug text not null unique,
  category text,
  description text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table job_skills (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references jobs(id) on delete cascade,
  skill_id uuid not null references skills(id) on delete cascade,
  is_required boolean not null default true,
  level text check (level is null or level in ('beginner', 'intermediate', 'advanced', 'expert')),
  constraint job_skills_job_skill_unique unique (job_id, skill_id)
);

create table candidate_skills (
  id uuid primary key default gen_random_uuid(),
  job_seeker_id uuid not null references job_seekers(id) on delete cascade,
  skill_id uuid not null references skills(id) on delete cascade,
  level text check (level is null or level in ('beginner', 'intermediate', 'advanced', 'expert')),
  years_experience integer check (years_experience is null or years_experience >= 0),
  constraint candidate_skills_job_seeker_skill_unique unique (job_seeker_id, skill_id)
);

create table interview_sessions (
  id uuid primary key default gen_random_uuid(),
  job_seeker_id uuid not null references job_seekers(id) on delete cascade,
  job_id uuid references jobs(id) on delete set null,
  title text not null,
  session_type text not null check (session_type in ('mock', 'job_based', 'practice')),
  status text not null default 'created' check (status in ('created', 'in_progress', 'completed', 'cancelled')),
  total_questions integer not null default 0 check (total_questions >= 0),
  overall_score numeric(5,2) check (overall_score is null or overall_score between 0 and 100),
  ai_summary text,
  started_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table interview_questions (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references interview_sessions(id) on delete cascade,
  order_index integer not null check (order_index > 0),
  question_type text not null check (question_type in ('behavioral', 'technical', 'situational', 'general')),
  content text not null,
  difficulty text check (difficulty is null or difficulty in ('easy', 'medium', 'hard')),
  skill_tag text,
  time_limit_seconds integer check (time_limit_seconds is null or time_limit_seconds > 0),
  ai_generated boolean not null default true,
  constraint interview_questions_session_order_unique unique (session_id, order_index),
  constraint interview_questions_id_session_unique unique (id, session_id)
);

create table interview_answers (
  id uuid primary key default gen_random_uuid(),
  question_id uuid not null,
  session_id uuid not null references interview_sessions(id) on delete cascade,
  transcript_text text,
  audio_url text,
  video_url text,
  duration_seconds integer check (duration_seconds is null or duration_seconds >= 0),
  is_skipped boolean not null default false,
  answered_at timestamptz,
  constraint interview_answers_question_session_fk foreign key (question_id, session_id)
    references interview_questions(id, session_id) on delete cascade
);

create table ai_answer_feedbacks (
  id uuid primary key default gen_random_uuid(),
  answer_id uuid not null unique references interview_answers(id) on delete cascade,
  relevance_score numeric(5,2) check (relevance_score is null or relevance_score between 0 and 100),
  clarity_score numeric(5,2) check (clarity_score is null or clarity_score between 0 and 100),
  depth_score numeric(5,2) check (depth_score is null or depth_score between 0 and 100),
  confidence_score numeric(5,2) check (confidence_score is null or confidence_score between 0 and 100),
  overall_score numeric(5,2) check (overall_score is null or overall_score between 0 and 100),
  strengths text,
  weaknesses text,
  suggestions text,
  model_used text,
  generated_at timestamptz not null default now()
);

create table ai_session_feedbacks (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null unique references interview_sessions(id) on delete cascade,
  overall_score numeric(5,2) check (overall_score is null or overall_score between 0 and 100),
  ai_summary text,
  strengths text,
  weaknesses text,
  suggestions text,
  model_used text,
  generated_at timestamptz not null default now()
);

create table ai_ranking_jobs (
  id uuid primary key default gen_random_uuid(),
  employer_id uuid not null references employers(id) on delete cascade,
  job_id uuid not null references jobs(id) on delete cascade,
  ranking_criteria jsonb not null default '{}'::jsonb,
  status text not null default 'queued' check (status in ('queued', 'processing', 'completed', 'failed', 'cancelled')),
  total_candidates integer not null default 0 check (total_candidates >= 0),
  processed_candidates integer not null default 0 check (processed_candidates >= 0),
  jd_snapshot_hash text,
  error_message text,
  model_used text,
  requested_at timestamptz not null default now(),
  started_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now()
);

create table ai_ranking_results (
  id uuid primary key default gen_random_uuid(),
  ranking_job_id uuid not null references ai_ranking_jobs(id) on delete cascade,
  application_id uuid not null references applications(id) on delete cascade,
  rank_position integer check (rank_position is null or rank_position > 0),
  match_score numeric(5,2) check (match_score is null or match_score between 0 and 100),
  ai_summary text,
  score_breakdown jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint ai_ranking_results_ranking_application_unique unique (ranking_job_id, application_id),
  constraint ai_ranking_results_ranking_position_unique unique (ranking_job_id, rank_position)
);

create table ai_job_recommendations (
  id uuid primary key default gen_random_uuid(),
  job_seeker_id uuid not null references job_seekers(id) on delete cascade,
  job_id uuid not null references jobs(id) on delete cascade,
  match_score numeric(5,2) check (match_score is null or match_score between 0 and 100),
  reason_json jsonb not null default '{}'::jsonb,
  model_used text,
  generated_at timestamptz not null default now(),
  is_viewed boolean not null default false,
  constraint ai_job_recommendations_job_seeker_job_unique unique (job_seeker_id, job_id)
);

create table plans (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  target_role text not null check (target_role in ('job_seeker', 'employer', 'all')),
  description text,
  price numeric(14,2) not null default 0 check (price >= 0),
  currency text not null default 'VND',
  duration_days integer not null check (duration_days > 0),
  features jsonb not null default '{}'::jsonb,
  status text not null default 'active' check (status in ('active', 'inactive', 'archived')),
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  plan_id uuid not null references plans(id) on delete restrict,
  status text not null default 'pending' check (status in ('pending', 'active', 'expired', 'cancelled')),
  start_date timestamptz,
  end_date timestamptz,
  cancelled_at timestamptz,
  cancelled_reason text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint subscriptions_date_range_check check (
    start_date is null or end_date is null or start_date <= end_date
  )
);

create table payments (
  id uuid primary key default gen_random_uuid(),
  subscription_id uuid references subscriptions(id) on delete set null,
  user_id uuid not null references users(id) on delete cascade,
  amount numeric(14,2) not null check (amount >= 0),
  currency text not null default 'VND',
  payment_method text check (payment_method is null or payment_method in ('bank_transfer', 'credit_card', 'momo', 'vnpay', 'paypal', 'cash')),
  gateway text,
  gateway_order_id text,
  status text not null default 'pending' check (status in ('pending', 'paid', 'failed', 'cancelled', 'refunded')),
  transaction_id text unique,
  gateway_response jsonb not null default '{}'::jsonb,
  failure_reason text,
  paid_at timestamptz,
  created_at timestamptz not null default now()
);

create table subscription_usages (
  id uuid primary key default gen_random_uuid(),
  subscription_id uuid not null references subscriptions(id) on delete cascade,
  feature_key text not null,
  used_count integer not null default 0 check (used_count >= 0),
  limit_count integer check (limit_count is null or limit_count >= 0),
  reset_at timestamptz,
  updated_at timestamptz not null default now(),
  constraint subscription_usages_subscription_feature_unique unique (subscription_id, feature_key)
);

create table payment_webhook_events (
  id uuid primary key default gen_random_uuid(),
  payment_id uuid references payments(id) on delete set null,
  gateway text not null,
  event_type text not null,
  payload_json jsonb not null default '{}'::jsonb,
  status text not null default 'received' check (status in ('received', 'processed', 'failed', 'ignored')),
  error_message text,
  received_at timestamptz not null default now(),
  processed_at timestamptz
);

create table question_banks (
  id uuid primary key default gen_random_uuid(),
  created_by_user_id uuid not null references users(id) on delete restrict,
  reviewed_by_user_id uuid references users(id) on delete set null,
  title text not null,
  content text not null,
  question_type text not null check (question_type in ('behavioral', 'technical', 'situational', 'general')),
  difficulty text check (difficulty is null or difficulty in ('easy', 'medium', 'hard')),
  skill_tag text,
  category text,
  status text not null default 'draft' check (status in ('draft', 'pending_review', 'approved', 'rejected', 'archived')),
  suggested_answer text,
  usage_count integer not null default 0 check (usage_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  title text not null,
  message text not null,
  type text not null,
  ref_id uuid,
  ref_type text,
  is_read boolean not null default false,
  read_at timestamptz,
  link_url text,
  channel text not null default 'in_app' check (channel in ('in_app', 'email', 'sms', 'push')),
  created_at timestamptz not null default now()
);

create table admin_audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_user_id uuid references users(id) on delete set null,
  action text not null,
  target_type text not null,
  target_id uuid,
  old_value_json jsonb,
  new_value_json jsonb,
  ip_address inet,
  created_at timestamptz not null default now()
);


create or replace function check_application_resume_owner()
returns trigger as $$
begin
  if new.resume_id is not null and not exists (
    select 1
    from resumes r
    where r.id = new.resume_id
      and r.job_seeker_id = new.job_seeker_id
  ) then
    raise exception 'Resume % does not belong to job seeker %', new.resume_id, new.job_seeker_id;
  end if;
  return new;
end;
$$ language plpgsql;

create trigger applications_check_resume_owner
before insert or update of resume_id, job_seeker_id on applications
for each row execute function check_application_resume_owner();

create or replace function check_ai_ranking_result_job()
returns trigger as $$
begin
  if not exists (
    select 1
    from ai_ranking_jobs arj
    join applications a on a.job_id = arj.job_id
    where arj.id = new.ranking_job_id
      and a.id = new.application_id
  ) then
    raise exception 'Application % does not belong to the ranking job %', new.application_id, new.ranking_job_id;
  end if;
  return new;
end;
$$ language plpgsql;

create trigger ai_ranking_results_check_job
before insert or update of ranking_job_id, application_id on ai_ranking_results
for each row execute function check_ai_ranking_result_job();

create index users_role_idx on users(role);
create index users_status_idx on users(status);
create index oauth_accounts_user_id_idx on oauth_accounts(user_id);
create index email_verification_tokens_user_id_idx on email_verification_tokens(user_id);
create index password_reset_tokens_user_id_idx on password_reset_tokens(user_id);
create index job_seekers_user_id_idx on job_seekers(user_id);
create index employers_user_id_idx on employers(user_id);
create index employers_company_id_idx on employers(company_id);
create index company_documents_company_id_idx on company_documents(company_id);
create index company_documents_status_idx on company_documents(status);
create index categories_parent_id_idx on categories(parent_id);
create index jobs_company_id_idx on jobs(company_id);
create index jobs_category_id_idx on jobs(category_id);
create index jobs_created_by_employer_id_idx on jobs(created_by_employer_id);
create index jobs_reviewed_by_user_id_idx on jobs(reviewed_by_user_id);
create index jobs_status_idx on jobs(status);
create index jobs_deadline_idx on jobs(deadline);
create index jobs_location_idx on jobs(location);
create index jobs_job_type_idx on jobs(job_type);
create index jobs_work_mode_idx on jobs(work_mode);
create index jobs_experience_level_idx on jobs(experience_level);
create index resumes_job_seeker_id_idx on resumes(job_seeker_id);
create index resumes_content_json_gin_idx on resumes using gin(content_json);
create unique index resumes_one_primary_per_job_seeker_idx on resumes(job_seeker_id) where is_primary = true;
create index applications_job_id_idx on applications(job_id);
create index applications_job_seeker_id_idx on applications(job_seeker_id);
create index applications_resume_id_idx on applications(resume_id);
create index applications_status_updated_by_idx on applications(status_updated_by);
create index applications_status_idx on applications(status);
create index application_status_history_application_id_idx on application_status_history(application_id);
create index application_status_history_changed_by_user_id_idx on application_status_history(changed_by_user_id);
create index application_internal_notes_application_id_idx on application_internal_notes(application_id);
create index application_internal_notes_employer_id_idx on application_internal_notes(employer_id);
create index interview_schedules_application_id_idx on interview_schedules(application_id);
create index interview_schedules_employer_id_idx on interview_schedules(employer_id);
create index interview_schedules_job_seeker_id_idx on interview_schedules(job_seeker_id);
create index saved_jobs_job_id_idx on saved_jobs(job_id);
create index job_skills_skill_id_idx on job_skills(skill_id);
create index candidate_skills_skill_id_idx on candidate_skills(skill_id);
create index interview_sessions_job_seeker_id_idx on interview_sessions(job_seeker_id);
create index interview_sessions_job_id_idx on interview_sessions(job_id);
create index interview_questions_session_id_idx on interview_questions(session_id);
create index interview_answers_question_id_idx on interview_answers(question_id);
create index interview_answers_session_id_idx on interview_answers(session_id);
create index ai_ranking_jobs_employer_id_idx on ai_ranking_jobs(employer_id);
create index ai_ranking_jobs_job_id_idx on ai_ranking_jobs(job_id);
create index ai_ranking_results_application_id_idx on ai_ranking_results(application_id);
create index ai_job_recommendations_job_seeker_id_idx on ai_job_recommendations(job_seeker_id);
create index ai_job_recommendations_job_id_idx on ai_job_recommendations(job_id);
create index subscriptions_user_id_idx on subscriptions(user_id);
create index subscriptions_plan_id_idx on subscriptions(plan_id);
create unique index subscriptions_one_active_per_user_idx on subscriptions(user_id) where status = 'active';
create index payments_subscription_id_idx on payments(subscription_id);
create index payments_user_id_idx on payments(user_id);
create index subscription_usages_subscription_id_idx on subscription_usages(subscription_id);
create index payment_webhook_events_payment_id_idx on payment_webhook_events(payment_id);
create index question_banks_created_by_user_id_idx on question_banks(created_by_user_id);
create index question_banks_reviewed_by_user_id_idx on question_banks(reviewed_by_user_id);
create index notifications_user_id_idx on notifications(user_id);
create index notifications_unread_idx on notifications(user_id, created_at) where is_read = false;
create index admin_audit_logs_actor_user_id_idx on admin_audit_logs(actor_user_id);
create index admin_audit_logs_target_idx on admin_audit_logs(target_type, target_id);

create trigger users_set_updated_at
before update on users
for each row execute function set_updated_at();

create trigger oauth_accounts_set_updated_at
before update on oauth_accounts
for each row execute function set_updated_at();

create trigger companies_set_updated_at
before update on companies
for each row execute function set_updated_at();

create trigger company_documents_set_updated_at
before update on company_documents
for each row execute function set_updated_at();

create trigger job_seekers_set_updated_at
before update on job_seekers
for each row execute function set_updated_at();

create trigger employers_set_updated_at
before update on employers
for each row execute function set_updated_at();

create trigger categories_set_updated_at
before update on categories
for each row execute function set_updated_at();

create trigger jobs_set_updated_at
before update on jobs
for each row execute function set_updated_at();

create trigger resumes_set_updated_at
before update on resumes
for each row execute function set_updated_at();

create trigger applications_set_updated_at
before update on applications
for each row execute function set_updated_at();

create trigger application_internal_notes_set_updated_at
before update on application_internal_notes
for each row execute function set_updated_at();

create trigger interview_schedules_set_updated_at
before update on interview_schedules
for each row execute function set_updated_at();

create trigger skills_set_updated_at
before update on skills
for each row execute function set_updated_at();

create trigger interview_sessions_set_updated_at
before update on interview_sessions
for each row execute function set_updated_at();

create trigger plans_set_updated_at
before update on plans
for each row execute function set_updated_at();

create trigger subscriptions_set_updated_at
before update on subscriptions
for each row execute function set_updated_at();

create trigger subscription_usages_set_updated_at
before update on subscription_usages
for each row execute function set_updated_at();

create trigger question_banks_set_updated_at
before update on question_banks
for each row execute function set_updated_at();
