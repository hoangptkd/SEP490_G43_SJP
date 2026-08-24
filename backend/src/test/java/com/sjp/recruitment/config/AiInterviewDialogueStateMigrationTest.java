package com.sjp.recruitment.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiInterviewDialogueStateMigrationTest {

    private static final String MIGRATION =
            "db/migration/V61__drop_ai_interview_dialogue_state_check.sql";

    @Test
    void removesDialogueStateConstraintWithoutReplacingIt() throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(MIGRATION)) {
            assertNotNull(input, "Missing migration " + MIGRATION);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ")
                    .trim()
                    .toLowerCase(Locale.ROOT);

            assertTrue(sql.contains("alter table interview_sessions drop constraint if exists "
                    + "interview_sessions_dialogue_state_check"));
            assertFalse(sql.contains("add constraint"));
            assertFalse(sql.contains("check ("));
        }
    }
}
