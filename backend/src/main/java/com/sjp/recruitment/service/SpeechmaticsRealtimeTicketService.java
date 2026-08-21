package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.AiInterviewTranscriptionTicketResponse;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.repository.InterviewSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Service
public class SpeechmaticsRealtimeTicketService {
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_OUTSTANDING_TICKETS = 1_000;

    private final AiInterviewProperties properties;
    private final CandidateService candidateService;
    private final InterviewSessionRepository sessionRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, TicketClaim> tickets = new HashMap<>();

    public SpeechmaticsRealtimeTicketService(
            AiInterviewProperties properties,
            CandidateService candidateService,
            InterviewSessionRepository sessionRepository) {
        this.properties = properties;
        this.candidateService = candidateService;
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public AiInterviewTranscriptionTicketResponse create(String sessionIdValue) {
        ensureAvailable();
        UUID sessionId = parseUuid(sessionIdValue);
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        InterviewSession session = sessionRepository
                .findByIdAndCandidateIdAndDeletedAtIsNull(sessionId, candidate.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND",
                        "Không tìm thấy phiên phỏng vấn"));
        if (session.isCompleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_COMPLETED",
                    "Phiên phỏng vấn đã hoàn thành");
        }

        Instant expiresAt = Instant.now().plusSeconds(effectiveTtlSeconds());
        String ticket = issue(new TicketClaim(candidate.getId(), sessionId, expiresAt));
        return new AiInterviewTranscriptionTicketResponse(
                "speechmatics_realtime",
                "/ws/ai-interview/transcription?ticket=" + ticket,
                expiresAt.toEpochMilli(),
                Math.max(1_000, properties.getSpeechmaticsFinalFlushTimeoutMs())
        );
    }

    public synchronized TicketClaim consume(String ticket) {
        cleanupExpired();
        TicketClaim claim = ticket == null ? null : tickets.remove(ticket);
        if (claim == null || claim.expiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TRANSCRIPTION_TICKET_INVALID",
                    "Phiên nhận dạng giọng nói không còn hợp lệ");
        }
        return claim;
    }

    private synchronized String issue(TicketClaim claim) {
        cleanupExpired();
        if (tickets.size() >= MAX_OUTSTANDING_TICKETS) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TRANSCRIPTION_TICKET_CAPACITY_REACHED",
                    "Hệ thống đang có quá nhiều yêu cầu nhận dạng giọng nói");
        }
        byte[] bytes = new byte[TOKEN_BYTES];
        String ticket;
        do {
            secureRandom.nextBytes(bytes);
            ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (tickets.containsKey(ticket));
        tickets.put(ticket, claim);
        return ticket;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        Iterator<TicketClaim> iterator = tickets.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expiresAt().isBefore(now)) iterator.remove();
        }
    }

    private void ensureAvailable() {
        if (!"speechmatics_realtime".equalsIgnoreCase(properties.getAnswerTranscriptionProvider())
                || !properties.isAnswerTranscriptionConfigured()) {
            throw new ApiException(HttpStatus.CONFLICT, "SPEECHMATICS_REALTIME_NOT_AVAILABLE",
                    "Speechmatics Realtime chưa được cấu hình");
        }
    }

    private long effectiveTtlSeconds() {
        return Math.max(10, Math.min(300, properties.getSpeechmaticsTicketTtlSeconds()));
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SESSION_ID_INVALID",
                    "Mã phiên phỏng vấn không hợp lệ");
        }
    }

    public record TicketClaim(UUID candidateId, UUID interviewSessionId, Instant expiresAt) {
    }
}
