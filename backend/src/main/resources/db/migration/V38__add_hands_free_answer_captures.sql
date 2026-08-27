alter table interview_answers
  add column if not exists original_speech_transcript text,
  add column if not exists speech_analysis_json jsonb not null default '{}'::jsonb,
  add column if not exists active_capture_id uuid,
  add column if not exists active_capture_version integer;

create table if not exists interview_answer_captures (
  id uuid primary key default gen_random_uuid(),
  answer_id uuid not null references interview_answers(id) on delete cascade,
  capture_id uuid not null,
  capture_version integer not null,
  payload_hash varchar(64) not null,
  status varchar(32) not null default 'processing',
  browser_transcript text,
  gladia_transcript text,
  final_transcript text,
  transcript_status varchar(64),
  data_quality varchar(64),
  vad_metrics_json jsonb not null default '{}'::jsonb,
  error_code varchar(100),
  created_at timestamp not null default now(),
  updated_at timestamp default now(),
  constraint interview_answer_capture_version_unique unique (answer_id, capture_id, capture_version),
  constraint interview_answer_capture_version_positive check (capture_version > 0)
);

create index if not exists interview_answer_captures_lookup_idx
  on interview_answer_captures(answer_id, capture_id, capture_version desc);
