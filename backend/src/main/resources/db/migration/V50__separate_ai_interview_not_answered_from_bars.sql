alter table ai_answer_feedbacks
  add column evaluation_status varchar(30) not null default 'RATED',
  add column bars_level integer,
  add column score_reason varchar(40),
  add constraint ai_answer_feedbacks_evaluation_status_check
    check (evaluation_status in ('RATED', 'NOT_ANSWERED')),
  add constraint ai_answer_feedbacks_bars_level_check
    check (bars_level is null or bars_level between 1 and 5),
  add constraint ai_answer_feedbacks_score_reason_check
    check (score_reason is null or score_reason in ('SKIPPED', 'NOT_ANSWERED'));
