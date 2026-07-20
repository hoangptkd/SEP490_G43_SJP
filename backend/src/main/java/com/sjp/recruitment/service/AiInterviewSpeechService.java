package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.AiInterviewSpeechTicketResponse;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.service.ai.AiInterviewRateLimiter;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AiInterviewSpeechService {

    private static final long TICKET_TTL_SECONDS = 120;
    private static final int MAX_INPUT_LENGTH = 2_000;

    private final CandidateService candidateService;
    private final AiInterviewRateLimiter rateLimiter;
    private final ShopAiKeyClient shopAiKeyClient;
    private final AiInterviewProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, SpeechTicket> tickets = new ConcurrentHashMap<>();

    public AiInterviewSpeechTicketResponse createTicket(String input) {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_INTERVIEW_DISABLED", "AI Interview chưa được cấu hình.");
        }
        if (!"shopaikey_tts".equalsIgnoreCase(properties.getVoiceProvider())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TTS_PROVIDER_DISABLED", "Chưa bật ShopAIKey TTS.");
        }
        String normalizedInput = input == null ? "" : input.replaceAll("\\s+", " ").trim();
        if (normalizedInput.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TTS_INPUT_REQUIRED", "Nội dung đọc không được để trống.");
        }
        if (normalizedInput.length() > MAX_INPUT_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TTS_INPUT_TOO_LONG", "Nội dung đọc vượt quá giới hạn cho phép.");
        }
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "speech");
        cleanupExpiredTickets();

        String token = newToken();
        long expiresAt = Instant.now().plusSeconds(TICKET_TTL_SECONDS).toEpochMilli();
        tickets.put(token, new SpeechTicket(normalizedInput, shopAiKeyClient.speechContentType(), expiresAt));
        return new AiInterviewSpeechTicketResponse(
                "/api/candidate/ai-interviews/speech/" + token,
                shopAiKeyClient.speechContentType(),
                expiresAt
        );
    }

    public String contentTypeForTicket(String token) {
        return requireTicket(token).contentType();
    }

    public void streamSpeech(String token, OutputStream outputStream) {
        SpeechTicket ticket = requireTicket(token);
        shopAiKeyClient.streamSpeech(ticket.input(), outputStream);
    }

    private SpeechTicket requireTicket(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TTS_TICKET_NOT_FOUND", "Không tìm thấy phiên audio.");
        }
        SpeechTicket ticket = tickets.get(token);
        if (ticket == null || ticket.expiresAt() < Instant.now().toEpochMilli()) {
            tickets.remove(token);
            throw new ApiException(HttpStatus.NOT_FOUND, "TTS_TICKET_EXPIRED", "Phiên audio đã hết hạn.");
        }
        return ticket;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void cleanupExpiredTickets() {
        long now = Instant.now().toEpochMilli();
        Iterator<Map.Entry<String, SpeechTicket>> iterator = tickets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt() < now) {
                iterator.remove();
            }
        }
    }

    private record SpeechTicket(String input, String contentType, long expiresAt) {
    }
}
