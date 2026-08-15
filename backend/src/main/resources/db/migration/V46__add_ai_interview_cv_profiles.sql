create table ai_interview_cv_profiles (
  id uuid primary key default gen_random_uuid(),
  job_seeker_id uuid not null references job_seekers(id) on delete cascade,
  cv_id uuid not null references resumes(id) on delete cascade,
  content_hash varchar(64) not null,
  profile_json jsonb not null default '{}'::jsonb,
  prompt_version varchar(80) not null,
  model_used varchar(120) not null,
  created_at timestamptz not null default now(),
  constraint ai_interview_cv_profiles_cache_unique
    unique (job_seeker_id, cv_id, content_hash, prompt_version, model_used)
);

create index ai_interview_cv_profiles_cv_lookup_idx
  on ai_interview_cv_profiles(job_seeker_id, cv_id, created_at desc);
