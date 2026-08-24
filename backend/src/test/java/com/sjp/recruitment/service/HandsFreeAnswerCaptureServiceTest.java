package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.HandsFreeAnswerCaptureResponse;
import com.sjp.recruitment.model.entity.*;
import com.sjp.recruitment.model.enums.InterviewDialogueState;
import com.sjp.recruitment.model.enums.InterviewTurnAnswerStatus;
import com.sjp.recruitment.model.enums.InterviewTurnType;
import com.sjp.recruitment.model.enums.TranscriptCorrectionStatus;
import com.sjp.recruitment.model.enums.VoiceEvidenceStatus;
import com.sjp.recruitment.repository.*;
import com.sjp.recruitment.service.ai.*;
import com.sjp.recruitment.service.audio.AudioDecoder;
import com.sjp.recruitment.service.audio.DecodedPcmAudio;
import com.sjp.recruitment.service.audio.PcmWaveWriter;
import com.sjp.recruitment.service.vad.SileroVadAnalyzer;
import com.sjp.recruitment.service.vad.VadAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.task.SyncTaskExecutor;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HandsFreeAnswerCaptureServiceTest {
    private final UUID candidateId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private final UUID answerId = UUID.randomUUID();
    private final AiInterviewProperties properties = new AiInterviewProperties();
    private final CandidateService candidateService = mock(CandidateService.class);
    private final AiInterviewRateLimiter rateLimiter = mock(AiInterviewRateLimiter.class);
    private final InterviewSessionRepository sessionRepository = mock(InterviewSessionRepository.class);
    private final InterviewQuestionRepository questionRepository = mock(InterviewQuestionRepository.class);
    private final InterviewAnswerRepository answerRepository = mock(InterviewAnswerRepository.class);
    private final InterviewAnswerCaptureRepository captureRepository = mock(InterviewAnswerCaptureRepository.class);
    private final InterviewConversationTurnRepository turnRepository = mock(InterviewConversationTurnRepository.class);
    private final TechnicalVocabularyBuilder vocabularyBuilder = mock(TechnicalVocabularyBuilder.class);
    private final TranscriptCorrectionContextBuilder correctionContextBuilder = mock(TranscriptCorrectionContextBuilder.class);
    private final TranscriptCorrectionService transcriptCorrectionService = mock(TranscriptCorrectionService.class);
    private final GladiaVoiceEvidenceService voiceEvidenceService = mock(GladiaVoiceEvidenceService.class);
    private final AudioDecoder audioDecoder = mock(AudioDecoder.class);
    private final PcmWaveWriter waveWriter = mock(PcmWaveWriter.class);
    private final SileroVadAnalyzer vadAnalyzer = mock(SileroVadAnalyzer.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final AtomicReference<InterviewAnswerCapture> persistedCapture = new AtomicReference<>();
    private final List<String> persistedAnswerStatuses = new ArrayList<>();
    private InterviewSession session;
    private InterviewQuestion question;
    private InterviewAnswer answer;
    private HandsFreeAnswerCaptureService service;

    @BeforeEach
    void setUp() {
        properties.setTranscriptCorrectionEnabled(true);
        persistedAnswerStatuses.clear();
        CandidateProfile candidate = new CandidateProfile();
        candidate.setId(candidateId);
        session = new InterviewSession();
        session.setId(sessionId);
        session.setCandidate(candidate);
        session.setStatus("in_progress");
        session.setTitle("Backend Developer");
        session.setOverallScore(new BigDecimal("77.50"));
        question = new InterviewQuestion();
        question.setId(questionId);
        question.setSession(session);
        question.setOrderIndex(1);
        question.setContent("Bạn dùng Spring Boot và REST API như thế nào?");
        answer = new InterviewAnswer();
        answer.setId(answerId);
        answer.setSession(session);
        answer.setQuestionId(questionId);

        when(candidateService.getCurrentCandidateProfile()).thenReturn(candidate);
        when(sessionRepository.findOwnedForUpdate(sessionId, candidateId)).thenReturn(Optional.of(session));
        when(questionRepository.findByIdAndSessionId(questionId, sessionId)).thenReturn(Optional.of(question));
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(sessionId)).thenReturn(List.of(question));
        when(answerRepository.findBySessionIdAndQuestionId(sessionId, questionId)).thenReturn(Optional.of(answer));
        when(answerRepository.findById(answerId)).thenReturn(Optional.of(answer));
        when(answerRepository.save(any(InterviewAnswer.class))).thenAnswer(invocation -> {
            InterviewAnswer saved = invocation.getArgument(0);
            persistedAnswerStatuses.add(saved.getTranscriptStatus());
            return saved;
        });
        when(captureRepository.existsByCaptureIdAndAnswerIdNot(any(), eq(answerId))).thenReturn(false);
        when(captureRepository.findByAnswerIdAndCaptureIdAndCaptureVersion(eq(answerId), any(), anyInt()))
                .thenAnswer(invocation -> {
                    InterviewAnswerCapture capture = persistedCapture.get();
                    if (capture == null) return Optional.empty();
                    UUID captureId = invocation.getArgument(1);
                    int version = invocation.getArgument(2);
                    return capture.getCaptureId().equals(captureId) && capture.getCaptureVersion() == version
                            ? Optional.of(capture) : Optional.empty();
                });
        when(captureRepository.findTopByAnswerIdAndCaptureIdOrderByCaptureVersionDesc(eq(answerId), any()))
                .thenAnswer(invocation -> Optional.ofNullable(persistedCapture.get()));
        when(captureRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InterviewAnswerCapture capture = invocation.getArgument(0);
            capture.setId(UUID.randomUUID());
            persistedCapture.set(capture);
            return capture;
        });
        when(captureRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(vocabularyBuilder.build(session, question)).thenReturn(new GladiaTranscriptionContext(List.of("Spring Boot", "REST API")));
        when(correctionContextBuilder.build(eq(session), eq(question), anyString(), any()))
                .thenAnswer(invocation -> new TranscriptCorrectionContext(
                        question.getContent(), invocation.getArgument(2), List.of(), List.of(), List.of(), ""));
        when(audioDecoder.decode(anyList())).thenReturn(new DecodedPcmAudio(new short[16_000], 16_000));
        when(waveWriter.toByteArray(any())).thenReturn(new byte[]{'R', 'I', 'F', 'F'});
        when(vadAnalyzer.analyze(any(DecodedPcmAudio.class))).thenReturn(completedVad());
        when(transactions.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        service = new HandsFreeAnswerCaptureService(properties, candidateService, rateLimiter, sessionRepository,
                questionRepository, answerRepository, captureRepository, turnRepository, vocabularyBuilder,
                correctionContextBuilder, transcriptCorrectionService, voiceEvidenceService,
                audioDecoder, waveWriter, vadAnalyzer, transactions, new ObjectMapper(),
                new SyncTaskExecutor());
    }

    @Test
    void keepsWebSpeechAuthoritativeAndQueuesGladiaVoiceEvidence() {
        UUID captureId = UUID.randomUUID();
        BigDecimal originalScore = session.getOverallScore();

        HandsFreeAnswerCaptureResponse result = service.process(sessionId.toString(), questionId.toString(),
                captureId.toString(), captureId.toString(), 1,
                List.of(audio("one.webm", "one"), audio("two.webm", "two")), List.of(0, 1),
                "em dùng spring bút và rét api", List.of(1.0, 1.2));

        assertThat(result.gladiaTranscript()).isNull();
        assertThat(result.rawTranscript()).isEqualTo("em dùng spring bút và rét api");
        assertThat(result.correctedTranscript()).isEqualTo(result.rawTranscript());
        assertThat(result.correctionStatus()).isEqualTo("PENDING");
        assertThat(result.correctionCount()).isZero();
        assertThat(result.transcriptStatus()).isEqualTo("web_speech");
        assertThat(result.dataQuality()).isEqualTo("BROWSER_TRANSCRIPT_ONLY");
        assertThat(result.vadMetrics()).isNull();
        assertThat(answer.getTranscriptText()).isEqualTo(result.rawTranscript());
        assertThat(answer.getRawTranscript()).isEqualTo(result.rawTranscript());
        assertThat(answer.getFinalTranscript()).isEqualTo(result.rawTranscript());
        assertThat(answer.getConversationState()).isEqualTo("REVIEWING_TRANSCRIPT");
        assertThat(answer.getOriginalSpeechTranscript()).isNull();
        assertThat(answer.getSpeechAnalysisJson()).containsEntry("source", "handsFree");
        assertThat(answer.getTranscriptStatus()).isEqualTo("completed");
        assertThat(persistedCapture.get().getVoiceEvidenceStatus()).isEqualTo(VoiceEvidenceStatus.PENDING);
        assertThat(persistedAnswerStatuses).containsExactly("processing", "completed");
        assertThat(session.getOverallScore()).isEqualByComparingTo(originalScore);
        verify(audioDecoder).decode(argThat(segments -> segments.size() == 2));
        verify(voiceEvidenceService).submit(eq(answerId), eq(captureId), eq(1), any(byte[].class),
                eq(new GladiaTranscriptionContext(List.of("Spring Boot", "REST API"))));
        verify(transcriptCorrectionService).submit(
                eq(sessionId), eq(answerId), eq(captureId), eq(1), any());
    }

    @Test
    void conversationCaptureIsBoundToCurrentTurnAndProducesEditableDraft() {
        InterviewConversationTurn turn = new InterviewConversationTurn();
        turn.setId(UUID.randomUUID());
        turn.setSession(session);
        turn.setAssessmentItem(question);
        turn.setTurnType(InterviewTurnType.CORE_QUESTION);
        turn.setSequenceNo(2);
        turn.setText(question.getContent());
        turn.setAnswerStatus(InterviewTurnAnswerStatus.WAITING);
        session.setDialogueState(InterviewDialogueState.WAITING_ANSWER);
        session.setCurrentTurn(turn);
        when(turnRepository.findByIdAndSessionId(turn.getId(), sessionId))
                .thenReturn(Optional.of(turn));
        when(turnRepository.save(any(InterviewConversationTurn.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UUID captureId = UUID.randomUUID();

        HandsFreeAnswerCaptureResponse result = service.processTurn(
                sessionId.toString(), turn.getId().toString(),
                captureId.toString(), captureId.toString(), 1,
                List.of(audio("answer.webm", "answer")), List.of(0),
                "em dùng spring bút", List.of(1.0));

        assertThat(result.rawTranscript()).isEqualTo("em dùng spring bút");
        assertThat(persistedCapture.get().getConversationTurn()).isSameAs(turn);
        assertThat(turn.getAnswerStatus()).isEqualTo(InterviewTurnAnswerStatus.REVIEWING);
        assertThat(turn.getCandidateRawAnswer()).isEqualTo(result.rawTranscript());
        assertThat(turn.getCandidateFinalAnswer()).isEqualTo(result.rawTranscript());
        verifyNoInteractions(transcriptCorrectionService);
    }

    @Test
    void preservesWebSpeechTranscriptWhenVadFails() {
        when(vadAnalyzer.analyze(any(DecodedPcmAudio.class))).thenThrow(new RuntimeException("vad failed"));
        UUID captureId = UUID.randomUUID();

        HandsFreeAnswerCaptureResponse result = service.process(sessionId.toString(), questionId.toString(),
                captureId.toString(), captureId.toString(), 1, List.of(audio("one.webm", "one")),
                List.of(0), "Bản transcript realtime", List.of(1.0));

        assertThat(result.rawTranscript()).isEqualTo("Bản transcript realtime");
        assertThat(result.transcriptStatus()).isEqualTo("web_speech");
        assertThat(result.dataQuality()).isEqualTo("BROWSER_TRANSCRIPT_ONLY");
        assertThat(result.vadMetrics()).isNull();
        assertThat(answer.getTranscriptStatus()).isEqualTo("completed");
        assertThat(answer.getRawTranscript()).isEqualTo("Bản transcript realtime");
        assertThat(answer.getConversationState()).isEqualTo("REVIEWING_TRANSCRIPT");
        assertThat(answer.getErrorMessage()).isNull();
        assertThat(persistedAnswerStatuses).containsExactly("processing", "completed");
    }

    @Test
    void returnsCachedResultForSameIdempotentPayloadWithoutQueuingGladiaTwice() {
        UUID captureId = UUID.randomUUID();
        List<MockMultipartFile> files = List.of(audio("one.webm", "same"));

        HandsFreeAnswerCaptureResponse first = service.process(sessionId.toString(), questionId.toString(),
                captureId.toString(), captureId.toString(), 1, List.copyOf(files), List.of(0), "spring bút",
                List.of(1.0));
        HandsFreeAnswerCaptureResponse retry = service.process(sessionId.toString(), questionId.toString(),
                captureId.toString(), captureId.toString(), 1, List.copyOf(files), List.of(0), "spring bút",
                List.of(1.0));

        assertThat(retry).isEqualTo(first);
        verify(voiceEvidenceService, times(1)).submit(eq(answerId), eq(captureId), eq(1),
                any(byte[].class), any());
        verify(transcriptCorrectionService, times(2))
                .submit(eq(sessionId), eq(answerId), eq(captureId), eq(1), any());
    }

    @Test
    void acceptsNextCaptureVersionWithSameCaptureIdAndAllAnswerSegments() {
        UUID captureId = UUID.randomUUID();
        service.process(sessionId.toString(), questionId.toString(), captureId.toString(), captureId.toString(), 1,
                List.of(audio("one.webm", "one")), List.of(0), "đoạn một", List.of(1.0));

        HandsFreeAnswerCaptureResponse continued = service.process(sessionId.toString(), questionId.toString(),
                captureId.toString(), captureId.toString(), 2,
                List.of(audio("one.webm", "one"), audio("two.webm", "two")), List.of(0, 1),
                "đoạn một đoạn hai", List.of(1.0, 1.0));

        assertThat(continued.captureVersion()).isEqualTo(2);
        assertThat(continued.captureId()).isEqualTo(captureId.toString());
        assertThat(continued.rawTranscript()).isEqualTo("đoạn một đoạn hai");
        verify(audioDecoder, times(2)).decode(anyList());
    }

    @Test
    void rejectsSameCaptureVersionWithDifferentPayload() {
        UUID captureId = UUID.randomUUID();
        service.process(sessionId.toString(), questionId.toString(), captureId.toString(), captureId.toString(), 1,
                List.of(audio("one.webm", "one")), List.of(0), "java", List.of(1.0));

        assertThatThrownBy(() -> service.process(sessionId.toString(), questionId.toString(),
                captureId.toString(), captureId.toString(), 1, List.of(audio("one.webm", "different")),
                List.of(0), "java", List.of(1.0)))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("IDEMPOTENCY_PAYLOAD_MISMATCH");
    }

    @Test
    void rejectsAudioForAStaleQuestion() {
        InterviewQuestion newer = new InterviewQuestion();
        newer.setId(UUID.randomUUID());
        newer.setSession(session);
        newer.setOrderIndex(1);
        question.setOrderIndex(2);
        when(questionRepository.findBySessionIdOrderByOrderIndexAsc(sessionId)).thenReturn(List.of(newer, question));
        when(answerRepository.findBySessionIdAndQuestionId(sessionId, newer.getId())).thenReturn(Optional.empty());
        UUID captureId = UUID.randomUUID();

        assertThatThrownBy(() -> service.process(sessionId.toString(), questionId.toString(),
                captureId.toString(), captureId.toString(), 1, List.of(audio("one.webm", "one")),
                List.of(0), "answer", List.of(1.0)))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("STALE_QUESTION");
    }

    @Test
    void rejectsInvalidAudioAndAlwaysCleansTemporaryCaptureDirectory() throws Exception {
        long before = temporaryCaptureDirectoryCount();
        UUID captureId = UUID.randomUUID();
        when(vadAnalyzer.analyze(any(DecodedPcmAudio.class))).thenThrow(new RuntimeException("vad failed"));
        service.process(sessionId.toString(), questionId.toString(), captureId.toString(), captureId.toString(), 1,
                List.of(audio("valid.webm", "valid")), List.of(0), "fallback", List.of(1.0));
        assertThat(temporaryCaptureDirectoryCount()).isEqualTo(before);

        persistedCapture.set(null);
        captureId = UUID.randomUUID();
        MockMultipartFile invalid = new MockMultipartFile("audioSegments", "fake.webm", "audio/webm", "not audio".getBytes());
        UUID invalidCaptureId = captureId;
        assertThatThrownBy(() -> service.process(sessionId.toString(), questionId.toString(),
                invalidCaptureId.toString(), invalidCaptureId.toString(), 1, List.of(invalid), List.of(0), "answer",
                List.of(1.0)))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("AUDIO_INVALID_CONTENT");
        assertThat(temporaryCaptureDirectoryCount()).isEqualTo(before);
    }

    private MockMultipartFile audio(String name, String content) {
        byte[] suffix = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bytes = new byte[suffix.length + 4];
        bytes[0] = 0x1a;
        bytes[1] = 0x45;
        bytes[2] = (byte) 0xdf;
        bytes[3] = (byte) 0xa3;
        System.arraycopy(suffix, 0, bytes, 4, suffix.length);
        return new MockMultipartFile("audioSegments", name, "audio/webm;codecs=opus", bytes);
    }

    private long temporaryCaptureDirectoryCount() throws Exception {
        try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.filter(path -> path.getFileName().toString().startsWith("sjp-handsfree-")).count();
        }
    }

    private VadAnalysisResult completedVad() {
        return new VadAnalysisResult(VadAnalysisResult.Status.COMPLETED, null, "v6.2.1",
                List.of(new VadAnalysisResult.SpeechSegment(100, 900)), 1.0, 0.8, 0.1, 0.9,
                0, 0.0, 0.0, 0.0, 5, 2);
    }
}
