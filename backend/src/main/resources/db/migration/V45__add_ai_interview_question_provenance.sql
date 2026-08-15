alter table interview_questions
  add column source_type text,
  add column source_id text,
  add column prompt_version text,
  add column rubric_version text;

update interview_questions
set source_type = case
  when ai_generated then 'AI_GENERATED'
  else 'QUESTION_BANK'
end;

alter table interview_questions
  alter column source_type set not null,
  add constraint interview_questions_source_type_check
    check (source_type in ('AI_GENERATED', 'QUESTION_BANK'));

create index interview_questions_source_idx
  on interview_questions(source_type, source_id);
