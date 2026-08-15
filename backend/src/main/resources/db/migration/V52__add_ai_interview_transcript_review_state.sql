alter table interview_answers
  add column if not exists raw_transcript text,
  add column if not exists final_transcript text,
  add column if not exists transcript_edited boolean not null default false,
  add column if not exists transcript_edit_count integer not null default 0,
  add column if not exists conversation_state varchar(40) not null default 'LISTENING';

alter table interview_answer_captures
  add column if not exists raw_transcript text;

update interview_answer_captures
set raw_transcript = coalesce(raw_transcript, final_transcript);

update interview_answers
set raw_transcript = coalesce(raw_transcript, original_speech_transcript, transcript_text),
    final_transcript = case when answered_at is not null then coalesce(final_transcript, transcript_text) else final_transcript end,
    conversation_state = case
      when answered_at is not null then 'ANSWER_CONFIRMED'
      when transcript_status = 'processing' then 'PROCESSING_AUDIO'
      when coalesce(raw_transcript, original_speech_transcript, transcript_text) is not null then 'REVIEWING_TRANSCRIPT'
      else 'LISTENING'
    end;

alter table interview_answers
  add constraint interview_answers_transcript_edit_count_check check (transcript_edit_count >= 0),
  add constraint interview_answers_conversation_state_check check (conversation_state in (
    'AI_SPEAKING', 'LISTENING', 'WAITING_FOR_CONTINUATION', 'PROCESSING_AUDIO',
    'REVIEWING_TRANSCRIPT', 'ANSWER_CONFIRMED', 'NEXT_QUESTION'
  ));
