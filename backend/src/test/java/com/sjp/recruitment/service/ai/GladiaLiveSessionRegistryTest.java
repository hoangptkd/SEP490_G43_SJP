package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GladiaLiveSessionRegistryTest {
    private final GladiaLiveSessionRegistry registry = new GladiaLiveSessionRegistry();

    @Test
    void bindsOneClaimToItsCandidateTargetAndCapture() {
        UUID candidateId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID captureId = UUID.randomUUID();
        UUID token = registry.register(candidateId, sessionId, "turn", targetId, "gladia-job");

        registry.validateAndBind(token, candidateId, sessionId, "turn", targetId, captureId);
        registry.validateAndBind(token, candidateId, sessionId, "turn", targetId, captureId);

        assertThatThrownBy(() -> registry.validateAndBind(
                token, candidateId, sessionId, "turn", targetId, UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("GLADIA_LIVE_SESSION_REUSED");
        assertThatThrownBy(() -> registry.validateAndBind(
                token, UUID.randomUUID(), sessionId, "turn", targetId, captureId))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("GLADIA_LIVE_SESSION_INVALID");
    }
}
