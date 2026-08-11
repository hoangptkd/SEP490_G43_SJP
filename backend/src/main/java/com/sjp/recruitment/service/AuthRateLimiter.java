package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AuthRateLimitProperties;
import com.sjp.recruitment.exception.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AuthRateLimiter {

    private static final int CLEANUP_INTERVAL = 256;

    private final AuthRateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> requests = new ConcurrentHashMap<>();
    private final AtomicInteger requestCounter = new AtomicInteger();

    @Autowired
    public AuthRateLimiter(AuthRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AuthRateLimiter(AuthRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void check(Operation operation, String identifier, String remoteAddress) {
        if (!properties.isEnabled()) {
            return;
        }
        Instant now = clock.instant();
        int limit = limitFor(operation);
        checkBucket("ip:" + operation + ":" + hash(remoteAddress), limit, now);
        checkBucket("identity:" + operation + ":" + hash(normalize(identifier)), limit, now);
        if (requestCounter.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            cleanup(now);
        }
    }

    private void checkBucket(String key, int limit, Instant now) {
        Duration window = Duration.ofSeconds(Math.max(1, properties.getWindowSeconds()));
        Deque<Instant> timestamps = requests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            removeExpired(timestamps, now, window);
            if (timestamps.size() >= Math.max(1, limit)) {
                long retryAfter = Math.max(1, Duration.between(now, timestamps.peekFirst().plus(window)).getSeconds());
                throw new ApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "AUTH_RATE_LIMITED",
                        "Qua nhieu yeu cau. Vui long thu lai sau.",
                        retryAfter
                );
            }
            timestamps.addLast(now);
        }
    }

    private void cleanup(Instant now) {
        Duration window = Duration.ofSeconds(Math.max(1, properties.getWindowSeconds()));
        requests.entrySet().removeIf(entry -> {
            Deque<Instant> timestamps = entry.getValue();
            synchronized (timestamps) {
                removeExpired(timestamps, now, window);
                return timestamps.isEmpty();
            }
        });
    }

    private void removeExpired(Deque<Instant> timestamps, Instant now, Duration window) {
        Instant threshold = now.minus(window);
        while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(threshold)) {
            timestamps.removeFirst();
        }
    }

    private int limitFor(Operation operation) {
        return switch (operation) {
            case LOGIN -> properties.getLoginRequests();
            case REGISTER -> properties.getRegisterRequests();
            case FORGOT_PASSWORD -> properties.getForgotPasswordRequests();
            case RESET_PASSWORD -> properties.getResetPasswordRequests();
            case RESEND_VERIFICATION -> properties.getResendVerificationRequests();
        };
    }

    private String normalize(String value) {
        return value == null ? "unknown" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalize(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public enum Operation {
        LOGIN,
        REGISTER,
        FORGOT_PASSWORD,
        RESET_PASSWORD,
        RESEND_VERIFICATION
    }
}
