package com.sjp.recruitment.model.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.sjp.recruitment.model.dto.profile.ProjectItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void applicationRequiresPreferredLocationAndLimitsCoverLetter() {
        ApplicationSubmitRequest missingLocation = new ApplicationSubmitRequest(
                "00000000-0000-0000-0000-000000000001",
                "00000000-0000-0000-0000-000000000002",
                null,
                " ",
                "cover"
        );
        ApplicationSubmitRequest longCoverLetter = new ApplicationSubmitRequest(
                "00000000-0000-0000-0000-000000000001",
                "00000000-0000-0000-0000-000000000002",
                null,
                "Ha Noi",
                "x".repeat(2001)
        );

        assertFalse(validator.validate(missingLocation).isEmpty());
        assertFalse(validator.validate(longCoverLetter).isEmpty());
    }

    @Test
    void reportReasonAndDescriptionUseDocumentedLimits() {
        assertTrue(validator.validate(new JobReportRequest("misleading", "x".repeat(2000))).isEmpty());
        assertFalse(validator.validate(new JobReportRequest(" ", "ok")).isEmpty());
        assertFalse(validator.validate(new JobReportRequest("misleading", "x".repeat(2001))).isEmpty());
    }

    @Test
    void profileRejectsFutureBirthDateInvalidPhoneAndMalformedSectionUrl() {
        CandidateProfileRequest request = new CandidateProfileRequest(
                "Candidate", "abc", LocalDate.now().plusDays(1), "Ha Noi", "Bio", List.of("Java"),
                "Backend Developer", 2, "junior", "https://linkedin.com/in/candidate", "https://example.com",
                List.of(), List.of(), List.of(new ProjectItem("Project", "Developer", "2026", "Description", "javascript:alert(1)")), List.of()
        );
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void jobAlertUsesEnumAndNonNegativeSalaryConstraints() {
        JobAlertRequest request = new JobAlertRequest(
                "Backend jobs", "Java", "Ha Noi", "it", "invalid", "remote",
                new BigDecimal("-1"), new BigDecimal("10"), "HOURLY", true
        );
        assertFalse(validator.validate(request).isEmpty());
    }
}
