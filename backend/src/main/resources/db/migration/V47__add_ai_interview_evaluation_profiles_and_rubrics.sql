alter table interview_sessions
  add column evaluation_profile_json jsonb not null default '{}'::jsonb;

alter table interview_questions
  add column competency_id varchar(100),
  add column rubric_json jsonb not null default '{}'::jsonb;

create index interview_questions_competency_idx
  on interview_questions(session_id, competency_id);
