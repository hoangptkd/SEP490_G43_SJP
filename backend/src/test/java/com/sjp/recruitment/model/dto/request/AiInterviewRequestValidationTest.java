package com.sjp.recruitment.model.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiInterviewRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsOversizedAndBlankPracticeContext() {
        AiInterviewPracticeSessionRequest oversized = new AiInterviewPracticeSessionRequest(
                "x".repeat(121),
                Collections.nCopies(13, "Java"),
                null,
                null
        );
        AiInterviewPracticeSessionRequest blankSkill = new AiInterviewPracticeSessionRequest(
                "Backend Developer",
                List.of(" "),
                null,
                null
        );

        assertFalse(validator.validate(oversized).isEmpty());
        assertFalse(validator.validate(blankSkill).isEmpty());
    }

    @Test
    void acceptsBoundedPracticeContext() {
        AiInterviewPracticeSessionRequest request = new AiInterviewPracticeSessionRequest(
                "Backend Developer",
                List.of("Java", "Spring Boot"),
                null,
                null
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsExcessiveTranscript() {
        assertFalse(validator.validate(new AiInterviewSubmitAnswerRequest("x".repeat(12_001))).isEmpty());
    }

    @Test
    void validatesOptionalFinishDraftBounds() {
        assertTrue(validator.validate(new AiInterviewFinishRequest(null, null)).isEmpty());
        assertFalse(validator.validate(new AiInterviewFinishRequest("x".repeat(37), "answer")).isEmpty());
        assertFalse(validator.validate(new AiInterviewFinishRequest(null, "x".repeat(12_001))).isEmpty());
    }
}
