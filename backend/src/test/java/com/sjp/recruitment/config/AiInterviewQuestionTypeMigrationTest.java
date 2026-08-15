package com.sjp.recruitment.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiInterviewQuestionTypeMigrationTest {

    private static final String MIGRATION =
            "db/migration/V54__allow_cv_experience_question_type.sql";

    @Test
    void allowsCvExperienceInSessionAndQuestionBankConstraints() throws IOException {
        String sql = normalizedMigrationSql();
        String allowedTypes = "'behavioral', 'technical', 'situational', 'cv_experience', 'general'";

        assertTrue(sql.contains(
                "alter table interview_questions "
                        + "drop constraint interview_questions_question_type_check, "
                        + "add constraint interview_questions_question_type_check "
                        + "check (question_type in ( " + allowedTypes + " ))"));
        assertTrue(sql.contains(
                "alter table ai_question_bank "
                        + "drop constraint ai_question_bank_question_type_check, "
                        + "add constraint ai_question_bank_question_type_check "
                        + "check (question_type in ( " + allowedTypes + " ))"));
    }

    private String normalizedMigrationSql() throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(MIGRATION)) {
            assertNotNull(input, "Missing migration " + MIGRATION);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ")
                    .trim()
                    .toLowerCase(Locale.ROOT);
        }
    }
}
