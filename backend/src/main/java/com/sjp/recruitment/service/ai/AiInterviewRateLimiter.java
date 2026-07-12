package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class AiInterviewRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int CLEANUP_INTERVAL = 256;

    private final AiInterviewProperties properties;
    private final Clock clock = Clock.systemUTC();
    private final Map<String, Deque<Instant>> requests = new ConcurrentHashMap<>();
    private final AtomicInteger requestCounter = new AtomicInteger();

    public void check(UUID candidateId, String operation) {
        Instant now = clock.instant();
        String key = candidateId + ":" + operation;
        Deque<Instant> timestamps = requests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            removeExpired(timestamps, now);
            if (timestamps.size() >= Math.max(1, properties.getCostlyRequestsPerMinute())) {
                throw new ApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "AI_INTERVIEW_RATE_LIMITED",
                        "Bạn thao tác quá nhanh. Vui lòng chờ một phút rồi thử lại."
                );
            }
            timestamps.addLast(now);
        }
        if (requestCounter.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            cleanup(now);
        }
    }

    private void cleanup(Instant now) {
        requests.entrySet().removeIf(entry -> {
            Deque<Instant> timestamps = entry.getValue();
            synchronized (timestamps) {
                removeExpired(timestamps, now);
                return timestamps.isEmpty();
            }
        });
    }

    private void removeExpired(Deque<Instant> timestamps, Instant now) {
        Instant threshold = now.minus(WINDOW);
        while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(threshold)) {
            timestamps.removeFirst();
        }
    }
}
