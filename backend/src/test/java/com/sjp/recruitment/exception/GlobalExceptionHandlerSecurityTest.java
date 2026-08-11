package com.sjp.recruitment.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerSecurityTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedErrorsDoNotExposeInternalExceptionMessages() {
        var response = handler.handleUnexpected(new RuntimeException("database-password=secret"));

        assertFalse(response.getBody().message().contains("database-password"));
        assertFalse(response.getBody().message().contains("secret"));
    }

    @Test
    void dataConflictsDoNotExposeConstraintDetails() {
        var response = handler.handleConcurrentWrite(new DataIntegrityViolationException("users_email_unique"));

        assertFalse(response.getBody().message().contains("users_email_unique"));
    }
}
