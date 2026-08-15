package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.AiInterviewLiveTranscriptionResponse;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.InterviewConversationTurn;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.enums.InterviewDialogueState;
import com.sjp.recruitment.model.enums.InterviewTurnAnswerStatus;
import com.sjp.recruitment.repository.InterviewAnswerRepository;
import com.sjp.recruitment.repository.InterviewConversationTurnRepository;
import com.sjp.recruitment.repository.InterviewQuestionRepository;
import com.sjp.recruitment.repository.InterviewSessionRepository;
import com.sjp.recruitment.service.ai.AiInterviewRateLimiter;
import com.sjp.recruitment.service.ai.GladiaLiveSessionClient;
import com.sjp.recruitment.service.ai.GladiaLiveSessionRegistry;
import com.sjp.recruitment.service.ai.GladiaTranscriptionContext;
import com.sjp.recruitment.service.ai.TechnicalVocabularyBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GladiaLiveSessionServiceTest {
    private final AiInterviewProperties properties = new AiInterviewProperties();
    private final CandidateService candidateService = mock(CandidateService.class);
    private final AiInterviewRateLimiter rateLimiter = mock(AiInterviewRateLimiter.class);
    private final InterviewSessionRepository sessionRepository = mock(InterviewSessionRepository.class);
    private final InterviewQuestionRepository questionRepository = mock(InterviewQuestionRepository.class);
    private final InterviewAnswerRepository answerRepository = mock(InterviewAnswerRepository.class);
    private final InterviewConversationTurnRepository turnRepository = mock(InterviewConversationTurnRepository.class);
    private final TechnicalVocabularyBuilder vocabularyBuilder = mock(TechnicalVocabularyBuilder.class);
    private final GladiaLiveSessionClient client = mock(GladiaLiveSessionClient.class);
    private final GladiaLiveSessionRegistry registry = mock(GladiaLiveSessionRegistry.class);
    private final GladiaLiveSessionService service = new GladiaLiveSessionService(
            properties, candidateService, rateLimiter, sessionRepository, questionRepository, answerRepository,
            turnRepository, vocabularyBuilder, client, registry);

    @Test
    void createsCandidateBoundLiveSessionForCurrentConversationTurn() {
        properties.setAnswerTranscriptionProvider("gladia_live");
        UUID candidateId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        CandidateProfile candidate = new CandidateProfile();
        candidate.setId(candidateId);
        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setCandidate(candidate);
        session.setStatus("in_progress");
        session.setDialogueState(InterviewDialogueState.WAITING_ANSWER);
        InterviewQuestion question = new InterviewQuestion();
        question.setId(questionId);
        question.setSession(session);
        InterviewConversationTurn turn = new InterviewConversationTurn();
        turn.setId(turnId);
        turn.setSession(session);
        turn.setAssessmentItem(question);
        turn.setAnswerStatus(InterviewTurnAnswerStatus.WAITING);
        GladiaTranscriptionContext context = new GladiaTranscriptionContext(List.of("Spring Boot"));

        when(candidateService.getCurrentCandidateProfile()).thenReturn(candidate);
        when(sessionRepository.findByIdAndCandidateIdAndDeletedAtIsNull(sessionId, candidateId))
                .thenReturn(Optional.of(session));
        when(turnRepository.findCurrentBySessionId(sessionId)).thenReturn(Optional.of(turn));
        when(vocabularyBuilder.build(session, question)).thenReturn(context);
        when(client.start(48_000, context)).thenReturn(new GladiaLiveSessionClient.LiveSession(
                "gladia-job", "wss://api.gladia.io/v2/live?token=temporary"));
        when(registry.register(candidateId, sessionId, "turn", turnId, "gladia-job")).thenReturn(token);

        AiInterviewLiveTranscriptionResponse response = service.create(sessionId.toString(), 48_000);

        assertThat(response.provider()).isEqualTo("gladia_live");
        assertThat(response.sessionToken()).isEqualTo(token.toString());
        assertThat(response.targetType()).isEqualTo("turn");
        assertThat(response.targetId()).isEqualTo(turnId.toString());
        verify(rateLimiter).check(candidateId, "gladia-live-init");
    }

    @Test
    void rejectsUnsupportedMicrophoneSampleRateBeforeCreatingProviderSession() {
        properties.setAnswerTranscriptionProvider("gladia_live");

        assertThatThrownBy(() -> service.create(UUID.randomUUID().toString(), 24_000))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("AUDIO_SAMPLE_RATE_UNSUPPORTED");
        verifyNoInteractions(client, registry);
    }
}
