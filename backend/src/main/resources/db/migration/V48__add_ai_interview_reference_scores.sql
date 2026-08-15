alter table ai_session_feedbacks
  add column content_score numeric(5,2),
  add column voice_delivery_score numeric(5,2),
  add column voice_weight numeric(4,3),
  add column reference_only boolean not null default true,
  add column evaluation_profile_version varchar(80),
  add column rubric_version varchar(80),
  add column speech_calibration_version varchar(80),
  add constraint ai_session_feedbacks_content_score_check
    check (content_score is null or content_score between 0 and 100),
  add constraint ai_session_feedbacks_voice_score_check
    check (voice_delivery_score is null or voice_delivery_score between 0 and 100),
  add constraint ai_session_feedbacks_voice_weight_check
    check (voice_weight is null or voice_weight between 0.10 and 0.30);
