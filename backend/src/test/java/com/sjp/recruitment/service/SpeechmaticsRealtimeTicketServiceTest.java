package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.AiInterviewTranscriptionTicketResponse;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.repository.InterviewSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpeechmaticsRealtimeTicketServiceTest {
    private final AiInterviewProperties properties = new AiInterviewProperties();
    private final CandidateService candidateService = mock(CandidateService.class);
    private final InterviewSessionRepository sessionRepository = mock(InterviewSessionRepository.class);
    private final SpeechmaticsRealtimeTicketService service = new SpeechmaticsRealtimeTicketService(
            properties, candidateService, sessionRepository);

    @BeforeEach
    void configureSpeechmatics() {
        properties.setSpeechmaticsApiKey("test-key");
    }

    @Test
    void createsCandidateBoundOneTimeTicket() {
        UUID candidateId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        CandidateProfile candidate = new CandidateProfile();
        candidate.setId(candidateId);
        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setStatus("in_progress");
        when(candidateService.getCurrentCandidateProfile()).thenReturn(candidate);
        when(sessionRepository.findByIdAndCandidateIdAndDeletedAtIsNull(sessionId, candidateId))
                .thenReturn(Optional.of(session));

        AiInterviewTranscriptionTicketResponse response = service.create(sessionId.toString());
        String ticket = response.websocketPath().substring(response.websocketPath().indexOf("ticket=") + 7);

        assertThat(response.provider()).isEqualTo("speechmatics_realtime");
        assertThat(ticket).hasSizeGreaterThanOrEqualTo(40);
        assertThat(service.consume(ticket).interviewSessionId()).isEqualTo(sessionId);
        assertThatThrownBy(() -> service.consume(ticket))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("TRANSCRIPTION_TICKET_INVALID");
    }

    @Test
    void rejectsTicketForSessionNotOwnedByCandidate() {
        UUID candidateId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        CandidateProfile candidate = new CandidateProfile();
        candidate.setId(candidateId);
        when(candidateService.getCurrentCandidateProfile()).thenReturn(candidate);
        when(sessionRepository.findByIdAndCandidateIdAndDeletedAtIsNull(sessionId, candidateId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(sessionId.toString()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("SESSION_NOT_FOUND");
    }
}
