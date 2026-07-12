package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiInterviewRateLimiterTest {

    @Test
    void limitsCostlyOperationsPerCandidateAndOperation() {
        AiInterviewProperties properties = new AiInterviewProperties();
        properties.setCostlyRequestsPerMinute(2);
        AiInterviewRateLimiter limiter = new AiInterviewRateLimiter(properties);
        UUID candidateId = UUID.randomUUID();

        assertDoesNotThrow(() -> limiter.check(candidateId, "answer"));
        assertDoesNotThrow(() -> limiter.check(candidateId, "answer"));
        ApiException exception = assertThrows(ApiException.class, () -> limiter.check(candidateId, "answer"));

        assertEquals("AI_INTERVIEW_RATE_LIMITED", exception.getCode());
        assertDoesNotThrow(() -> limiter.check(candidateId, "transcribe"));
        assertDoesNotThrow(() -> limiter.check(UUID.randomUUID(), "answer"));
    }
}
