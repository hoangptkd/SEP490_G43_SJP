alter table interview_sessions
  add column dialogue_state varchar(32),
  add column current_turn_id uuid,
  add column next_turn_sequence integer not null default 1,
  add column assessment_turn_count integer not null default 0,
  add column evidence_summary_json jsonb not null default '{}'::jsonb,
  add column dialogue_version integer not null default 1,
  add column last_error_stage varchar(64),
  add column last_error_code varchar(100),
  add column last_error_message text,
  add constraint interview_sessions_dialogue_state_check check (dialogue_state is null or dialogue_state in (
    'SESSION_START', 'OPENING', 'ASK_CORE', 'WAITING_ANSWER', 'ANALYZE_ANSWER',
    'ASK_PROBE', 'ASK_CLARIFY', 'ACK_TRANSITION', 'CLOSING', 'COMPLETED'
  )),
  add constraint interview_sessions_next_turn_sequence_check check (next_turn_sequence > 0),
  add constraint interview_sessions_assessment_turn_count_check check (assessment_turn_count >= 0),
  add constraint interview_sessions_dialogue_version_check check (dialogue_version > 0);

alter table interview_answers
  add column evidence_summary_json jsonb not null default '{}'::jsonb;

create table interview_conversation_turns (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references interview_sessions(id) on delete cascade,
  assessment_item_id uuid,
  reply_to_turn_id uuid,
  sequence_no integer not null,
  turn_type varchar(32) not null,
  text text not null,
  candidate_raw_answer text,
  candidate_final_answer text,
  transcript_edited boolean not null default false,
  edit_count integer not null default 0,
  answer_status varchar(24) not null default 'NOT_REQUIRED',
  analysis_json jsonb not null default '{}'::jsonb,
  answer_client_id uuid,
  replay_count integer not null default 0,
  answered_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz default now(),
  constraint interview_conversation_turns_session_sequence_unique unique (session_id, sequence_no),
  constraint interview_conversation_turns_id_session_unique unique (id, session_id),
  constraint interview_conversation_turns_sequence_check check (sequence_no > 0),
  constraint interview_conversation_turns_type_check check (turn_type in (
    'OPENING', 'CORE_QUESTION', 'PROBE', 'CLARIFY',
    'ACKNOWLEDGEMENT', 'TRANSITION', 'CLOSING'
  )),
  constraint interview_conversation_turns_answer_status_check check (answer_status in (
    'NOT_REQUIRED', 'WAITING', 'PROCESSING', 'REVIEWING', 'CONFIRMED', 'SKIPPED'
  )),
  constraint interview_conversation_turns_edit_count_check check (edit_count >= 0),
  constraint interview_conversation_turns_replay_count_check check (replay_count >= 0),
  constraint interview_conversation_turns_non_answer_status_check check (
    turn_type in ('CORE_QUESTION', 'PROBE', 'CLARIFY') or answer_status = 'NOT_REQUIRED'
  ),
  constraint interview_conversation_turns_assessment_item_fk
    foreign key (assessment_item_id, session_id)
    references interview_questions(id, session_id) on delete cascade,
  constraint interview_conversation_turns_reply_fk
    foreign key (reply_to_turn_id, session_id)
    references interview_conversation_turns(id, session_id) on delete set null
);

alter table interview_sessions
  add constraint interview_sessions_current_turn_fk foreign key (current_turn_id)
    references interview_conversation_turns(id) on delete set null;

alter table interview_answer_captures
  add column conversation_turn_id uuid,
  add constraint interview_answer_captures_conversation_turn_fk foreign key (conversation_turn_id)
    references interview_conversation_turns(id) on delete set null;

create index interview_conversation_turns_assessment_item_idx
  on interview_conversation_turns(session_id, assessment_item_id, sequence_no);

create index interview_conversation_turns_reply_idx
  on interview_conversation_turns(reply_to_turn_id);

create index interview_conversation_turns_answer_status_idx
  on interview_conversation_turns(session_id, answer_status, sequence_no desc);

create unique index interview_conversation_turns_answer_client_unique
  on interview_conversation_turns(session_id, answer_client_id)
  where answer_client_id is not null;

create index interview_answer_captures_conversation_turn_idx
  on interview_answer_captures(conversation_turn_id);

create trigger interview_conversation_turns_set_updated_at
before update on interview_conversation_turns
for each row execute function set_updated_at();
