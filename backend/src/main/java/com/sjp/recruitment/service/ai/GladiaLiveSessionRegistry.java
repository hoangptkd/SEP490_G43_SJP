package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class GladiaLiveSessionRegistry {
    private static final Duration CLAIM_TTL = Duration.ofMinutes(30);
    private static final int MAX_CLAIMS = 1_000;

    private final Map<UUID, Claim> claims = new HashMap<>();

    public synchronized UUID register(
            UUID candidateId,
            UUID interviewSessionId,
            String targetType,
            UUID targetId,
            String jobId) {
        cleanupExpired();
        if (claims.size() >= MAX_CLAIMS) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GLADIA_LIVE_CAPACITY_REACHED",
                    "Hệ thống đang có quá nhiều phiên Gladia Live, vui lòng thử lại");
        }
        UUID token = UUID.randomUUID();
        claims.put(token, new Claim(candidateId, interviewSessionId, targetType, targetId, jobId,
                Instant.now().plus(CLAIM_TTL)));
        return token;
    }

    public synchronized void validateAndBind(
            UUID token,
            UUID candidateId,
            UUID interviewSessionId,
            String targetType,
            UUID targetId,
            UUID captureId) {
        cleanupExpired();
        Claim claim = claims.get(token);
        if (claim == null || !claim.matches(candidateId, interviewSessionId, targetType, targetId)) {
            throw new ApiException(HttpStatus.CONFLICT, "GLADIA_LIVE_SESSION_INVALID",
                    "Phiên Gladia Live không còn hợp lệ cho câu trả lời này");
        }
        if (claim.captureId != null && !claim.captureId.equals(captureId)) {
            throw new ApiException(HttpStatus.CONFLICT, "GLADIA_LIVE_SESSION_REUSED",
                    "Phiên Gladia Live đã được dùng cho một capture khác");
        }
        claim.captureId = captureId;
    }

    public synchronized void markCompleted(UUID token, UUID captureId) {
        Claim claim = claims.get(token);
        if (claim != null && Objects.equals(claim.captureId, captureId)) {
            claim.completed = true;
        }
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<UUID, Claim>> iterator = claims.entrySet().iterator();
        while (iterator.hasNext()) {
            Claim claim = iterator.next().getValue();
            if (claim.expiresAt.isBefore(now)) iterator.remove();
        }
    }

    private static final class Claim {
        private final UUID candidateId;
        private final UUID interviewSessionId;
        private final String targetType;
        private final UUID targetId;
        @SuppressWarnings("unused")
        private final String jobId;
        private final Instant expiresAt;
        private UUID captureId;
        @SuppressWarnings("unused")
        private boolean completed;

        private Claim(UUID candidateId, UUID interviewSessionId, String targetType, UUID targetId,
                      String jobId, Instant expiresAt) {
            this.candidateId = candidateId;
            this.interviewSessionId = interviewSessionId;
            this.targetType = targetType;
            this.targetId = targetId;
            this.jobId = jobId;
            this.expiresAt = expiresAt;
        }

        private boolean matches(UUID expectedCandidateId, UUID expectedSessionId,
                                String expectedTargetType, UUID expectedTargetId) {
            return candidateId.equals(expectedCandidateId)
                    && interviewSessionId.equals(expectedSessionId)
                    && targetType.equals(expectedTargetType)
                    && targetId.equals(expectedTargetId);
        }
    }
}
