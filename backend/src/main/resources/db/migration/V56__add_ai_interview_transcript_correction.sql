alter table interview_answer_captures
  add column transcript_correction_status varchar(32) not null default 'NOT_REQUIRED',
  add column corrected_transcript text,
  add column transcript_correction_json jsonb not null default '{}'::jsonb,
  add column transcript_correction_error_code varchar(100);

alter table interview_answer_captures
  add constraint interview_answer_captures_transcript_correction_status_check
    check (transcript_correction_status in (
      'PENDING', 'CORRECTED', 'UNCHANGED', 'FAILED', 'NOT_REQUIRED'
    ));
