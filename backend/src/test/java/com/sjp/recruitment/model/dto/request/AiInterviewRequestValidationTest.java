package com.sjp.recruitment.model.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiInterviewRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsOversizedAndBlankPracticeContext() {
        AiInterviewPracticeSessionRequest oversized = new AiInterviewPracticeSessionRequest(
                UUID.randomUUID().toString(),
                "x".repeat(121),
                "junior",
                List.of("Java")
        );
        AiInterviewPracticeSessionRequest blankSkill = new AiInterviewPracticeSessionRequest(
                UUID.randomUUID().toString(),
                "Backend Developer",
                "junior",
                List.of(" ")
        );
        AiInterviewPracticeSessionRequest invalidSeniority = new AiInterviewPracticeSessionRequest(
                UUID.randomUUID().toString(), "Backend Developer", "lead", List.of());
        AiInterviewPracticeSessionRequest tooManyFocusSkills = new AiInterviewPracticeSessionRequest(
                UUID.randomUUID().toString(), "Backend Developer", "junior",
                List.of("Java", "SQL", "Docker", "Redis"));

        assertFalse(validator.validate(oversized).isEmpty());
        assertFalse(validator.validate(blankSkill).isEmpty());
        assertFalse(validator.validate(invalidSeniority).isEmpty());
        assertFalse(validator.validate(tooManyFocusSkills).isEmpty());
    }

    @Test
    void acceptsBoundedPracticeContext() {
        AiInterviewPracticeSessionRequest request = new AiInterviewPracticeSessionRequest(
                UUID.randomUUID().toString(),
                "Backend Developer",
                "junior",
                List.of("Java", "Spring Boot")
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
