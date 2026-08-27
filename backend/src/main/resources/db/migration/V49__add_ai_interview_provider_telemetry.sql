create table ai_interview_provider_calls (
  id uuid primary key default gen_random_uuid(),
  session_id uuid,
  stage varchar(80) not null,
  model varchar(120) not null,
  prompt_version varchar(80) not null,
  input_tokens integer,
  output_tokens integer,
  latency_ms bigint not null,
  success boolean not null,
  error_code varchar(100),
  created_at timestamptz not null default now(),
  constraint ai_interview_provider_input_tokens_check check (input_tokens is null or input_tokens >= 0),
  constraint ai_interview_provider_output_tokens_check check (output_tokens is null or output_tokens >= 0),
  constraint ai_interview_provider_latency_check check (latency_ms >= 0)
);

create index ai_interview_provider_calls_session_stage_idx
  on ai_interview_provider_calls(session_id, stage, created_at desc);
