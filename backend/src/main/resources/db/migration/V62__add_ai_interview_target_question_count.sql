ALTER TABLE interview_sessions
    ADD COLUMN target_question_count INTEGER NOT NULL DEFAULT 5;

ALTER TABLE interview_sessions
    ADD CONSTRAINT chk_interview_sessions_target_question_count
        CHECK (target_question_count IN (3, 5, 7, 10));
