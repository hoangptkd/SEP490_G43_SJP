alter table interview_questions
  add column replay_count integer not null default 0,
  add constraint interview_questions_replay_count_check
    check (replay_count >= 0);

alter table ai_session_feedbacks
  drop constraint if exists ai_session_feedbacks_voice_weight_check;

alter table ai_session_feedbacks
  add column raw_voice_delivery_score numeric(5,2),
  add column replay_count integer not null default 0,
  add column replay_penalty numeric(5,2) not null default 0,
  add column voice_evidence_question_count integer not null default 0,
  add column manual_fallback_question_count integer not null default 0,
  add constraint ai_session_feedbacks_raw_voice_score_check
    check (raw_voice_delivery_score is null or raw_voice_delivery_score between 0 and 100),
  add constraint ai_session_feedbacks_replay_count_check
    check (replay_count >= 0),
  add constraint ai_session_feedbacks_replay_penalty_check
    check (replay_penalty between 0 and 10),
  add constraint ai_session_feedbacks_voice_evidence_count_check
    check (voice_evidence_question_count >= 0),
  add constraint ai_session_feedbacks_manual_fallback_count_check
    check (manual_fallback_question_count >= 0),
  add constraint ai_session_feedbacks_voice_weight_check
    check (voice_weight is null or voice_weight = 0 or voice_weight between 0.10 and 0.30);
