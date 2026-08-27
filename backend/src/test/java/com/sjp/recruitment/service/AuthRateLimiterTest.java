package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AuthRateLimitProperties;
import com.sjp.recruitment.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRateLimiterTest {

    private MutableClock clock;
    private AuthRateLimiter limiter;

    @BeforeEach
    void setUp() {
        AuthRateLimitProperties properties = new AuthRateLimitProperties();
        properties.setWindowSeconds(60);
        properties.setLoginRequests(1);
        properties.setRegisterRequests(1);
        properties.setForgotPasswordRequests(1);
        properties.setResetPasswordRequests(1);
        properties.setResendVerificationRequests(1);
        clock = new MutableClock(Instant.parse("2026-08-11T00:00:00Z"));
        limiter = new AuthRateLimiter(properties, clock);
    }

    @Test
    void limitsEveryPublicAuthOperation() {
        for (AuthRateLimiter.Operation operation : AuthRateLimiter.Operation.values()) {
            String identifier = operation + "@example.test";
            assertDoesNotThrow(() -> limiter.check(operation, identifier, "192.0.2." + operation.ordinal()));
            ApiException exception = assertThrows(
                    ApiException.class,
                    () -> limiter.check(operation, identifier, "192.0.2." + operation.ordinal())
            );
            assertEquals("AUTH_RATE_LIMITED", exception.getCode());
            assertTrue(exception.getRetryAfterSeconds() > 0);
        }
    }

    @Test
    void limitsAnIdentityAcrossDifferentAddressesAndResetsAfterWindow() {
        limiter.check(AuthRateLimiter.Operation.LOGIN, "USER@example.test", "192.0.2.1");
        assertThrows(ApiException.class, () ->
                limiter.check(AuthRateLimiter.Operation.LOGIN, "user@example.test", "192.0.2.2"));

        clock.advanceSeconds(61);

        assertDoesNotThrow(() ->
                limiter.check(AuthRateLimiter.Operation.LOGIN, "user@example.test", "192.0.2.2"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
