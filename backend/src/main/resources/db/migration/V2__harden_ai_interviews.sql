alter table interview_sessions
  add column if not exists version bigint not null default 0;

alter table interview_answers
  add column if not exists version bigint not null default 0,
  add column if not exists evaluation_source text not null default 'provider',
  add column if not exists evaluation_fallback boolean not null default false;

alter table ai_session_feedbacks
  add column if not exists evaluation_source text not null default 'provider',
  add column if not exists evaluation_fallback boolean not null default false;

update interview_answers
set evaluation_source = 'skipped', evaluation_fallback = false
where is_skipped = true;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'interview_answers_session_question_unique'
  ) then
    alter table interview_answers
      add constraint interview_answers_session_question_unique unique (session_id, question_id);
  end if;

  if not exists (
    select 1 from pg_constraint where conname = 'interview_answers_evaluation_source_check'
  ) then
    alter table interview_answers
      add constraint interview_answers_evaluation_source_check
      check (evaluation_source in ('provider', 'fallback', 'skipped'));
  end if;
end $$;

create index if not exists interview_sessions_candidate_history_idx
  on interview_sessions(job_seeker_id, updated_at desc)
  where deleted_at is null;

create index if not exists interview_answers_session_answered_idx
  on interview_answers(session_id, answered_at);
