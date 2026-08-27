alter table interview_answer_captures
  add column voice_evidence_status varchar(32) not null default 'NOT_REQUIRED',
  add column voice_evidence_error_code varchar(100);

update interview_answer_captures
set voice_evidence_status = 'COMPLETED'
where data_quality = 'AUDIO_VAD_PLUS_GLADIA';

alter table interview_answer_captures
  add constraint interview_answer_captures_voice_evidence_status_check
    check (voice_evidence_status in ('PENDING', 'COMPLETED', 'FAILED', 'NOT_REQUIRED'));

create index interview_answer_captures_voice_evidence_pending_idx
  on interview_answer_captures(answer_id, voice_evidence_status)
  where voice_evidence_status = 'PENDING';
