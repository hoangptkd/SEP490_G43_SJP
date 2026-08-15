alter table interview_questions
  drop constraint interview_questions_question_type_check,
  add constraint interview_questions_question_type_check check (question_type in (
    'behavioral', 'technical', 'situational', 'cv_experience', 'general'
  ));

alter table ai_question_bank
  drop constraint ai_question_bank_question_type_check,
  add constraint ai_question_bank_question_type_check check (question_type in (
    'behavioral', 'technical', 'situational', 'cv_experience', 'general'
  ));
