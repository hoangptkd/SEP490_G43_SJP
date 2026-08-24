package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewBackgroundConfiguration;
import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.HandsFreeAnswerCaptureResponse;
import com.sjp.recruitment.model.dto.response.HandsFreeVadMetricsResponse;
import com.sjp.recruitment.model.entity.*;
import com.sjp.recruitment.model.enums.InterviewTurnAnswerStatus;
import com.sjp.recruitment.model.enums.TranscriptCorrectionStatus;
import com.sjp.recruitment.model.enums.VoiceEvidenceStatus;
import com.sjp.recruitment.repository.*;
import com.sjp.recruitment.service.ai.*;
import com.sjp.recruitment.service.audio.AudioCaptureSegment;
import com.sjp.recruitment.service.audio.AudioDecodingException;
import com.sjp.recruitment.service.audio.AudioDecoder;
import com.sjp.recruitment.service.audio.DecodedPcmAudio;
import com.sjp.recruitment.service.audio.PcmWaveWriter;
import com.sjp.recruitment.service.vad.SileroVadAnalyzer;
import com.sjp.recruitment.service.vad.VadAnalysisException;
import com.sjp.recruitment.service.vad.VadAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class HandsFreeAnswerCaptureService {
    private static final String ANALYSIS_VERSION = "handsfree-vad-gladia-v1";

    private final AiInterviewProperties properties;
    private final CandidateService candidateService;
    private final AiInterviewRateLimiter rateLimiter;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final InterviewAnswerCaptureRepository captureRepository;
    private final InterviewConversationTurnRepository turnRepository;
    private final TechnicalVocabularyBuilder vocabularyBuilder;
    private final TranscriptCorrectionContextBuilder correctionContextBuilder;
    private final TranscriptCorrectionService transcriptCorrectionService;
    private final GladiaVoiceEvidenceService voiceEvidenceService;
    private final AudioDecoder audioDecoder;
    private final PcmWaveWriter waveWriter;
    private final SileroVadAnalyzer vadAnalyzer;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final TaskExecutor backgroundExecutor;

    public HandsFreeAnswerCaptureService(
            AiInterviewProperties properties,
            CandidateService candidateService,
            AiInterviewRateLimiter rateLimiter,
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            InterviewAnswerRepository answerRepository,
            InterviewAnswerCaptureRepository captureRepository,
            InterviewConversationTurnRepository turnRepository,
            TechnicalVocabularyBuilder vocabularyBuilder,
            TranscriptCorrectionContextBuilder correctionContextBuilder,
            TranscriptCorrectionService transcriptCorrectionService,
            GladiaVoiceEvidenceService voiceEvidenceService,
            AudioDecoder audioDecoder,
            PcmWaveWriter waveWriter,
            SileroVadAnalyzer vadAnalyzer,
            TransactionTemplate transactions,
            ObjectMapper objectMapper,
            @Qualifier(AiInterviewBackgroundConfiguration.EXECUTOR_BEAN) TaskExecutor backgroundExecutor) {
        this.properties = properties;
        this.candidateService = candidateService;
        this.rateLimiter = rateLimiter;
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.captureRepository = captureRepository;
        this.turnRepository = turnRepository;
        this.vocabularyBuilder = vocabularyBuilder;
        this.correctionContextBuilder = correctionContextBuilder;
        this.transcriptCorrectionService = transcriptCorrectionService;
        this.voiceEvidenceService = voiceEvidenceService;
        this.audioDecoder = audioDecoder;
        this.waveWriter = waveWriter;
        this.vadAnalyzer = vadAnalyzer;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.backgroundExecutor = backgroundExecutor;
    }

    public HandsFreeAnswerCaptureResponse process(
            String sessionIdValue,
            String questionIdValue,
            String idempotencyKey,
            String captureIdValue,
            int captureVersion,
            List<MultipartFile> audioSegments,
            List<Integer> segmentSequences,
            String browserTranscript,
            List<Double> durationSeconds) {
        return process(sessionIdValue, questionIdValue, idempotencyKey, captureIdValue, captureVersion,
                audioSegments, segmentSequences, browserTranscript, "web_speech", durationSeconds);
    }

    public HandsFreeAnswerCaptureResponse process(
            String sessionIdValue,
            String questionIdValue,
            String idempotencyKey,
            String captureIdValue,
            int captureVersion,
            List<MultipartFile> audioSegments,
            List<Integer> segmentSequences,
            String browserTranscript,
            String transcriptionProvider,
            List<Double> durationSeconds) {
        UUID sessionId = parseUuid(sessionIdValue, "SESSION_ID_INVALID");
        UUID questionId = parseUuid(questionIdValue, "QUESTION_ID_INVALID");
        return processInternal(
                sessionId,
                questionId,
                null,
                idempotencyKey,
                captureIdValue,
                captureVersion,
                audioSegments,
                segmentSequences,
                browserTranscript,
                transcriptionProvider,
                durationSeconds
        );
    }

    public HandsFreeAnswerCaptureResponse processTurn(
            String sessionIdValue,
            String turnIdValue,
            String idempotencyKey,
            String captureIdValue,
            int captureVersion,
            List<MultipartFile> audioSegments,
            List<Integer> segmentSequences,
            String browserTranscript,
            List<Double> durationSeconds) {
        return processTurn(sessionIdValue, turnIdValue, idempotencyKey, captureIdValue, captureVersion,
                audioSegments, segmentSequences, browserTranscript, "web_speech", durationSeconds);
    }

    public HandsFreeAnswerCaptureResponse processTurn(
            String sessionIdValue,
            String turnIdValue,
            String idempotencyKey,
            String captureIdValue,
            int captureVersion,
            List<MultipartFile> audioSegments,
            List<Integer> segmentSequences,
            String browserTranscript,
            String transcriptionProvider,
            List<Double> durationSeconds) {
        return processInternal(
                parseUuid(sessionIdValue, "SESSION_ID_INVALID"),
                null,
                parseUuid(turnIdValue, "TURN_ID_INVALID"),
                idempotencyKey,
                captureIdValue,
                captureVersion,
                audioSegments,
                segmentSequences,
                browserTranscript,
                transcriptionProvider,
                durationSeconds
        );
    }

    private HandsFreeAnswerCaptureResponse processInternal(
            UUID sessionId,
            UUID questionId,
            UUID turnId,
            String idempotencyKey,
            String captureIdValue,
            int captureVersion,
            List<MultipartFile> audioSegments,
            List<Integer> segmentSequences,
            String browserTranscript,
            String transcriptionProvider,
            List<Double> durationSeconds) {
        UUID captureId = parseUuid(captureIdValue, "CAPTURE_ID_INVALID");
        if (idempotencyKey == null || !idempotencyKey.equals(captureIdValue)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID", "Idempotency-Key phải trùng captureId");
        }
        validateRequest(captureVersion, audioSegments, segmentSequences, durationSeconds);
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "hands-free-transcribe");

        String normalizedProvider = normalizeTranscriptionProvider(transcriptionProvider);
        SpooledCapture spool = null;
        boolean audioJobOwnsSpool = false;
        try {
            spool = spool(audioSegments, segmentSequences, browserTranscript, normalizedProvider,
                    captureId, captureVersion, durationSeconds);
            SpooledCapture claimedSpool = spool;
            Claim claim = transactions.execute(status -> claim(
                    candidate.getId(), sessionId, questionId, turnId, captureId, captureVersion,
                    claimedSpool.payloadHash(), browserTranscript));
            if (claim == null) throw new IllegalStateException("Capture claim was not created");
            if (claim.cachedResponse() != null) {
                if (properties.isTranscriptCorrectionEnabled()
                        && turnId == null
                        && TranscriptCorrectionStatus.PENDING.name().equals(
                        claim.cachedResponse().correctionStatus())) {
                    transcriptCorrectionService.submit(
                            sessionId, claim.answerId(), captureId, captureVersion,
                            claim.correctionContext());
                }
                return claim.cachedResponse();
            }

            String normalizedBrowser = normalize(browserTranscript);
            String rawTranscript = normalizedBrowser;
            if (!hasText(rawTranscript)) {
                failCapture(claim.answerId(), captureId, captureVersion, "NO_TRANSCRIPT_AVAILABLE");
                throw new ApiException(HttpStatus.BAD_REQUEST, "NO_TRANSCRIPT_AVAILABLE",
                        "Nguồn nhận dạng realtime chưa tạo được transcript cho câu trả lời này");
            }

            String transcriptStatus = normalizedProvider;
            TranscriptCorrectionStatus correctionStatus = properties.isTranscriptCorrectionEnabled()
                    ? TranscriptCorrectionStatus.PENDING
                    : TranscriptCorrectionStatus.NOT_REQUIRED;
            HandsFreeAnswerCaptureResponse response = new HandsFreeAnswerCaptureResponse(
                    claim.questionId().toString(), captureId.toString(), captureVersion, normalizedBrowser,
                    null, rawTranscript, rawTranscript,
                    correctionStatus.name(), 0, transcriptStatus, dataQuality(false, false), null);
            HandsFreeAnswerCaptureResponse completed = transactions.execute(
                    status -> complete(claim.answerId(), response, null,
                            VoiceEvidenceStatus.PENDING, null));
            if (properties.isTranscriptCorrectionEnabled() && turnId == null) {
                transcriptCorrectionService.submit(
                        sessionId, claim.answerId(), captureId, captureVersion,
                        claim.correctionContext());
            }
            audioJobOwnsSpool = submitAudioProcessing(
                    claim.answerId(), captureId, captureVersion, spool, claim.context());
            return completed;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_UNREADABLE", "Không thể đọc audio đã tải lên");
        } finally {
            if (spool != null && !audioJobOwnsSpool) spool.close();
        }
    }

    @Transactional(readOnly = true)
    public HandsFreeAnswerCaptureResponse turnCaptureStatus(
            String sessionIdValue,
            String turnIdValue,
            String captureIdValue,
            int captureVersion
    ) {
        UUID sessionId = parseUuid(sessionIdValue, "SESSION_ID_INVALID");
        UUID turnId = parseUuid(turnIdValue, "TURN_ID_INVALID");
        UUID captureId = parseUuid(captureIdValue, "CAPTURE_ID_INVALID");
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        InterviewSession session = sessionRepository.findById(sessionId)
                .filter(item -> item.getCandidate().getId().equals(candidate.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "SESSION_NOT_FOUND", "Không tìm thấy phiên phỏng vấn"));
        InterviewConversationTurn turn = turnRepository.findByIdAndSessionId(turnId, session.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "INTERVIEW_TURN_NOT_FOUND", "Không tìm thấy lượt hội thoại"));
        InterviewAnswer answer = answerRepository.findBySessionIdAndQuestionId(
                        sessionId, turn.getAssessmentItem().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "ANSWER_NOT_FOUND", "Không tìm thấy câu trả lời"));
        InterviewAnswerCapture capture = captureRepository
                .findByAnswerIdAndCaptureIdAndCaptureVersion(
                        answer.getId(), captureId, Math.max(1, captureVersion))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "CAPTURE_NOT_FOUND", "Không tìm thấy kết quả audio"));
        HandsFreeAnswerCaptureResponse response = toResponse(turn.getAssessmentItem().getId(), capture);
        return new HandsFreeAnswerCaptureResponse(
                turnId.toString(), response.captureId(), response.captureVersion(),
                response.browserTranscript(), response.gladiaTranscript(), response.rawTranscript(),
                response.correctedTranscript(), response.correctionStatus(), response.correctionCount(),
                response.transcriptStatus(), response.dataQuality(), response.vadMetrics());
    }

    @Transactional(readOnly = true)
    public HandsFreeAnswerCaptureResponse questionCaptureStatus(
            String sessionIdValue,
            String questionIdValue,
            String captureIdValue,
            int captureVersion
    ) {
        UUID sessionId = parseUuid(sessionIdValue, "SESSION_ID_INVALID");
        UUID questionId = parseUuid(questionIdValue, "QUESTION_ID_INVALID");
        UUID captureId = parseUuid(captureIdValue, "CAPTURE_ID_INVALID");
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        InterviewSession session = sessionRepository.findById(sessionId)
                .filter(item -> item.getCandidate().getId().equals(candidate.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "SESSION_NOT_FOUND", "Không tìm thấy phiên phỏng vấn"));
        questionRepository.findByIdAndSessionId(questionId, session.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "QUESTION_NOT_FOUND", "Không tìm thấy câu hỏi"));
        InterviewAnswer answer = answerRepository.findBySessionIdAndQuestionId(sessionId, questionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "ANSWER_NOT_FOUND", "Không tìm thấy câu trả lời"));
        InterviewAnswerCapture capture = captureRepository
                .findByAnswerIdAndCaptureIdAndCaptureVersion(
                        answer.getId(), captureId, Math.max(1, captureVersion))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "CAPTURE_NOT_FOUND", "Không tìm thấy kết quả audio"));
        return toResponse(questionId, capture);
    }

    private boolean submitAudioProcessing(
            UUID answerId,
            UUID captureId,
            int captureVersion,
            SpooledCapture spool,
            GladiaTranscriptionContext context
    ) {
        try {
            backgroundExecutor.execute(() -> processAudioEvidence(
                    answerId, captureId, captureVersion, spool, context));
            return true;
        } catch (RuntimeException exception) {
            log.warn("Audio evidence executor rejected captureId={}, version={}", captureId, captureVersion);
            markAudioEvidenceFailed(answerId, captureId, captureVersion, "VOICE_EVIDENCE_CAPACITY");
            return false;
        }
    }

    private void processAudioEvidence(
            UUID answerId,
            UUID captureId,
            int captureVersion,
            SpooledCapture spool,
            GladiaTranscriptionContext context
    ) {
        try (spool) {
            PreparedAudio preparedAudio = prepareAudio(spool, true);
            VadAnalysisResult vadResult = preparedAudio.pcm() == null
                    ? analyzeVad(spool.segments()) : analyzeVad(preparedAudio.pcm());
            logIncompleteVad(vadResult);
            HandsFreeVadMetricsResponse vadMetrics = HandsFreeVadMetricsResponse.from(vadResult);
            transactions.executeWithoutResult(status -> updateAudioAnalysis(
                    answerId, captureId, captureVersion, vadResult, vadMetrics));
            voiceEvidenceService.submit(
                    answerId, captureId, captureVersion, preparedAudio.wavAudio(), context);
        } catch (RuntimeException exception) {
            log.warn("Background audio processing failed: captureId={}, version={}, code={}",
                    captureId, captureVersion, exception.getClass().getSimpleName());
            markAudioEvidenceFailed(answerId, captureId, captureVersion, "VOICE_EVIDENCE_FAILED");
        }
    }

    private void updateAudioAnalysis(
            UUID answerId,
            UUID captureId,
            int captureVersion,
            VadAnalysisResult vadResult,
            HandsFreeVadMetricsResponse vadMetrics
    ) {
        captureRepository.findByAnswerIdAndCaptureIdAndCaptureVersion(
                        answerId, captureId, captureVersion)
                .ifPresent(capture -> {
                    capture.setVadMetricsJson(vadMetrics == null
                            ? Map.of() : objectMapper.convertValue(vadMetrics, Map.class));
                    capture.setDataQuality(dataQuality(false, vadMetrics != null));
                    captureRepository.save(capture);
                    InterviewAnswer answer = capture.getAnswer();
                    HandsFreeAnswerCaptureResponse response = toResponse(answer.getQuestionId(), capture);
                    answer.setSpeechAnalysisJson(analysisJson(response, vadResult));
                    if (vadResult != null && vadResult.audioDurationSeconds() > 0) {
                        answer.setDurationSeconds(Math.max(1,
                                (int) Math.ceil(vadResult.audioDurationSeconds())));
                    }
                    answerRepository.save(answer);
                });
    }

    private void markAudioEvidenceFailed(
            UUID answerId,
            UUID captureId,
            int captureVersion,
            String errorCode
    ) {
        transactions.executeWithoutResult(status -> captureRepository
                .findByAnswerIdAndCaptureIdAndCaptureVersion(answerId, captureId, captureVersion)
                .ifPresent(capture -> {
                    capture.setVoiceEvidenceStatus(VoiceEvidenceStatus.FAILED);
                    capture.setVoiceEvidenceErrorCode(errorCode);
                    captureRepository.save(capture);
                }));
    }

    private Claim claim(UUID candidateId, UUID sessionId, UUID questionId, UUID turnId,
                        UUID captureId, int captureVersion,
                        String payloadHash, String browserTranscript) {
        InterviewSession session = sessionRepository.findOwnedForUpdate(sessionId, candidateId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Không tìm thấy phiên phỏng vấn"));
        if (session.isCompleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_COMPLETED", "Phiên phỏng vấn đã hoàn thành");
        }
        InterviewConversationTurn conversationTurn = null;
        InterviewQuestion requested;
        if (turnId != null) {
            if (session.getDialogueState() == null || session.getCurrentTurn() == null
                    || !turnId.equals(session.getCurrentTurn().getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "STALE_TURN",
                        "Audio không thuộc lượt hội thoại hiện tại");
            }
            conversationTurn = turnRepository.findByIdAndSessionId(turnId, sessionId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "INTERVIEW_TURN_NOT_FOUND", "Không tìm thấy lượt hội thoại"));
            if (conversationTurn.getAssessmentItem() == null
                    || (conversationTurn.getAnswerStatus() != InterviewTurnAnswerStatus.WAITING
                    && conversationTurn.getAnswerStatus() != InterviewTurnAnswerStatus.REVIEWING)) {
                throw new ApiException(HttpStatus.CONFLICT, "INTERVIEW_TURN_NOT_WAITING",
                        "Lượt hội thoại không ở trạng thái nhận câu trả lời");
            }
            requested = conversationTurn.getAssessmentItem();
            questionId = requested.getId();
        } else {
            if (session.getDialogueState() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "CONVERSATION_ENDPOINT_REQUIRED",
                        "Phiên này phải gửi audio theo currentTurnId");
            }
            requested = questionRepository.findByIdAndSessionId(questionId, sessionId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "QUESTION_NOT_FOUND", "Không tìm thấy câu hỏi"));
        }
        Optional<InterviewAnswer> existingAnswer = answerRepository.findBySessionIdAndQuestionId(sessionId, questionId);
        if (existingAnswer.isPresent()) {
            Optional<InterviewAnswerCapture> existingCapture = captureRepository
                    .findByAnswerIdAndCaptureIdAndCaptureVersion(existingAnswer.get().getId(), captureId, captureVersion);
            if (existingCapture.isPresent()) {
                InterviewAnswerCapture capture = existingCapture.get();
                if (!capture.getPayloadHash().equals(payloadHash)) {
                    throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_PAYLOAD_MISMATCH",
                            "Cùng captureId/version nhưng payload khác nhau");
                }
                if ("completed".equals(capture.getStatus())) {
                    return new Claim(existingAnswer.get().getId(), requested.getId(),
                            toResponse(requested.getId(), capture),
                            GladiaTranscriptionContext.empty(),
                            correctionContextBuilder.build(
                                    session,
                                    requested,
                                    capture.getRawTranscript(),
                                    existingAnswer.get().getEvidenceSummaryJson()));
                }
                throw new ApiException(HttpStatus.CONFLICT, "CAPTURE_PROCESSING", "Audio đang được chuẩn hóa");
            }
        }
        if (conversationTurn == null) {
            InterviewQuestion current = currentQuestion(sessionId);
            if (!current.getId().equals(requested.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "STALE_QUESTION", "Audio không thuộc câu hỏi hiện tại");
            }
        }
        InterviewAnswer answer = existingAnswer.orElseGet(() -> createDraft(session, requested));
        if (answer.getAnsweredAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "QUESTION_ALREADY_ANSWERED", "Câu hỏi này đã được chốt câu trả lời");
        }
        if (captureRepository.existsByCaptureIdAndAnswerIdNot(captureId, answer.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "CAPTURE_ID_REUSED", "captureId đã được dùng cho câu trả lời khác");
        }

        int expectedVersion = captureRepository.findTopByAnswerIdAndCaptureIdOrderByCaptureVersionDesc(answer.getId(), captureId)
                .map(previous -> previous.getCaptureVersion() + 1).orElse(1);
        if (captureVersion != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "CAPTURE_VERSION_INVALID",
                    "captureVersion phải tăng tuần tự cho cùng answer attempt");
        }

        InterviewAnswerCapture capture = new InterviewAnswerCapture();
        capture.setAnswer(answer);
        capture.setConversationTurn(conversationTurn);
        capture.setCaptureId(captureId);
        capture.setCaptureVersion(captureVersion);
        capture.setPayloadHash(payloadHash);
        capture.setBrowserTranscript(normalize(browserTranscript));
        captureRepository.saveAndFlush(capture);
        answer.setActiveCaptureId(captureId);
        answer.setActiveCaptureVersion(captureVersion);
        answer.setTranscriptStatus("processing");
        answer.setConversationState("PROCESSING_AUDIO");
        answer.setErrorMessage(null);
        answerRepository.save(answer);
        if (conversationTurn != null) {
            conversationTurn.setCandidateRawAnswer(normalize(browserTranscript));
            conversationTurn.setAnswerStatus(InterviewTurnAnswerStatus.PROCESSING);
            turnRepository.save(conversationTurn);
        }
        return new Claim(answer.getId(), requested.getId(), null,
                vocabularyBuilder.build(session, requested),
                correctionContextBuilder.build(
                        session, requested, browserTranscript, answer.getEvidenceSummaryJson()));
    }

    private HandsFreeAnswerCaptureResponse complete(
            UUID answerId,
            HandsFreeAnswerCaptureResponse response,
            VadAnalysisResult vadResult,
            VoiceEvidenceStatus voiceEvidenceStatus,
            String voiceEvidenceErrorCode) {
        InterviewAnswer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ANSWER_STALE", "Câu trả lời không còn tồn tại"));
        if (answer.getAnsweredAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "CAPTURE_STALE", "Câu trả lời đã được chốt trước khi audio xử lý xong");
        }
        if (!response.captureId().equals(String.valueOf(answer.getActiveCaptureId()))
                || !Objects.equals(response.captureVersion(), answer.getActiveCaptureVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "CAPTURE_STALE", "Kết quả audio đã cũ và bị bỏ qua");
        }
        UUID captureId = UUID.fromString(response.captureId());
        InterviewAnswerCapture capture = captureRepository
                .findByAnswerIdAndCaptureIdAndCaptureVersion(answerId, captureId, response.captureVersion())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "CAPTURE_STALE", "Không tìm thấy capture đang xử lý"));
        capture.setStatus("completed");
        capture.setBrowserTranscript(response.browserTranscript());
        capture.setGladiaTranscript(response.gladiaTranscript());
        capture.setRawTranscript(response.rawTranscript());
        capture.setTranscriptStatus(response.transcriptStatus());
        capture.setDataQuality(response.dataQuality());
        capture.setVoiceEvidenceStatus(voiceEvidenceStatus);
        capture.setVoiceEvidenceErrorCode(voiceEvidenceErrorCode);
        capture.setCorrectedTranscript(response.correctedTranscript());
        capture.setTranscriptCorrectionStatus(
                TranscriptCorrectionStatus.valueOf(response.correctionStatus()));
        capture.setTranscriptCorrectionJson(Map.of());
        capture.setTranscriptCorrectionErrorCode(null);
        capture.setVadMetricsJson(response.vadMetrics() == null
                ? Map.of() : objectMapper.convertValue(response.vadMetrics(), Map.class));
        captureRepository.save(capture);
        if (capture.getConversationTurn() != null) {
            InterviewConversationTurn turn = capture.getConversationTurn();
            turn.setCandidateRawAnswer(response.rawTranscript());
            turn.setCandidateFinalAnswer(response.rawTranscript());
            turn.setAnswerStatus(InterviewTurnAnswerStatus.REVIEWING);
            turnRepository.save(turn);
        }

        if (hasText(response.gladiaTranscript())) answer.setOriginalSpeechTranscript(response.gladiaTranscript());
        answer.setRawTranscript(response.rawTranscript());
        answer.setFinalTranscript(response.rawTranscript());
        answer.setTranscriptText(response.rawTranscript());
        answer.setSpeechAnalysisJson(analysisJson(response, vadResult));
        answer.setTranscriptStatus("completed");
        answer.setConversationState("REVIEWING_TRANSCRIPT");
        if (vadResult != null && vadResult.audioDurationSeconds() > 0) {
            answer.setDurationSeconds(Math.max(1, (int) Math.ceil(vadResult.audioDurationSeconds())));
        }
        answer.setErrorMessage(null);
        answerRepository.save(answer);
        return response;
    }

    private void failCapture(UUID answerId, UUID captureId, int version, String errorCode) {
        transactions.executeWithoutResult(status -> captureRepository
                .findByAnswerIdAndCaptureIdAndCaptureVersion(answerId, captureId, version)
                .ifPresent(capture -> {
                    capture.setStatus("failed");
                    capture.setErrorCode(errorCode);
                    captureRepository.save(capture);
                    InterviewAnswer answer = capture.getAnswer();
                    answer.setTranscriptStatus("failed");
                    answer.setErrorMessage(errorCode);
                    answer.setConversationState(hasText(answer.getRawTranscript())
                            ? "REVIEWING_TRANSCRIPT" : "LISTENING");
                    answerRepository.save(answer);
                    if (capture.getConversationTurn() != null) {
                        InterviewConversationTurn turn = capture.getConversationTurn();
                        if (!hasText(turn.getCandidateRawAnswer())
                                && hasText(capture.getBrowserTranscript())) {
                            turn.setCandidateRawAnswer(capture.getBrowserTranscript());
                        }
                        turn.setAnswerStatus(hasText(turn.getCandidateRawAnswer())
                                ? InterviewTurnAnswerStatus.REVIEWING
                                : InterviewTurnAnswerStatus.WAITING);
                        turnRepository.save(turn);
                    }
                }));
    }

    private PreparedAudio prepareAudio(SpooledCapture spool, boolean createNormalizedWav) {
        try {
            DecodedPcmAudio pcm = audioDecoder.decode(spool.segments().stream()
                    .map(segment -> AudioCaptureSegment.fromPath(segment.sequence(), segment.path(), segment.mimeType()))
                    .toList());
            byte[] wavAudio = createNormalizedWav ? waveWriter.toByteArray(pcm) : new byte[0];
            return new PreparedAudio(pcm, wavAudio);
        } catch (RuntimeException exception) {
            logAudioAnalysisFailure("prepare_audio", exception);
            return new PreparedAudio(null, new byte[0]);
        }
    }

    private VadAnalysisResult analyzeVad(List<SpooledSegment> segments) {
        try {
            return vadAnalyzer.analyze(segments.stream()
                    .map(segment -> AudioCaptureSegment.fromPath(segment.sequence(), segment.path(), segment.mimeType()))
                    .toList());
        } catch (RuntimeException exception) {
            logAudioAnalysisFailure("analyze_segments", exception);
            return null;
        }
    }

    private VadAnalysisResult analyzeVad(DecodedPcmAudio pcm) {
        try {
            return vadAnalyzer.analyze(pcm);
        } catch (RuntimeException exception) {
            logAudioAnalysisFailure("analyze_pcm", exception);
            return null;
        }
    }

    private void logIncompleteVad(VadAnalysisResult result) {
        if (result == null || result.completed()) return;
        log.warn("Hands-free VAD unavailable: status={}, errorCode={}",
                result.status(), safeFailureDetail(result.errorCode()));
    }

    private void logAudioAnalysisFailure(String stage, RuntimeException exception) {
        String code;
        if (exception instanceof AudioDecodingException decodingException) {
            code = decodingException.getCode().name();
        } else if (exception instanceof VadAnalysisException vadException) {
            code = vadException.getCode();
        } else {
            code = exception.getClass().getSimpleName();
        }
        log.warn("Hands-free audio analysis failed: stage={}, code={}, detail={}",
                stage, safeFailureDetail(code), safeFailureDetail(exception.getMessage()));
    }

    private String safeFailureDetail(String detail) {
        String normalized = detail == null || detail.isBlank()
                ? "không có chi tiết" : detail.replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(normalized.length(), 300));
    }

    private Map<String, Object> analysisJson(HandsFreeAnswerCaptureResponse response, VadAnalysisResult vad) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("analysisVersion", ANALYSIS_VERSION);
        json.put("captureId", response.captureId());
        json.put("captureVersion", response.captureVersion());
        json.put("source", "handsFree");
        json.put("dataQuality", response.dataQuality());
        json.put("transcriptEvidence", Map.of("gladiaTranscript",
                response.gladiaTranscript() == null ? "" : response.gladiaTranscript()));
        json.put("metrics", response.vadMetrics() == null ? Map.of() : objectMapper.convertValue(response.vadMetrics(), Map.class));
        Map<String, Object> vadStatus = new LinkedHashMap<>();
        vadStatus.put("modelVersion", vad == null ? null : vad.modelVersion());
        vadStatus.put("status", vad == null ? "unavailable" : vad.status().name().toLowerCase(Locale.ROOT));
        if (vad != null && vad.errorCode() != null) vadStatus.put("errorCode", vad.errorCode());
        json.put("vad", vadStatus);
        return json;
    }

    private HandsFreeAnswerCaptureResponse toResponse(UUID questionId, InterviewAnswerCapture capture) {
        HandsFreeVadMetricsResponse metrics = capture.getVadMetricsJson() == null || capture.getVadMetricsJson().isEmpty()
                ? null : objectMapper.convertValue(capture.getVadMetricsJson(), HandsFreeVadMetricsResponse.class);
        return new HandsFreeAnswerCaptureResponse(questionId.toString(), capture.getCaptureId().toString(),
                capture.getCaptureVersion(), capture.getBrowserTranscript(), capture.getGladiaTranscript(),
                capture.getRawTranscript(), reviewDraft(capture), capture.getTranscriptCorrectionStatus().name(),
                correctionCount(capture), capture.getTranscriptStatus(), capture.getDataQuality(), metrics);
    }

    private HandsFreeAnswerCaptureResponse withCorrection(
            HandsFreeAnswerCaptureResponse response,
            TranscriptCorrectionService.CorrectionResult correction
    ) {
        return new HandsFreeAnswerCaptureResponse(
                response.questionId(),
                response.captureId(),
                response.captureVersion(),
                response.browserTranscript(),
                response.gladiaTranscript(),
                response.rawTranscript(),
                correction.correctedTranscript(),
                correction.status().name(),
                correction.correctionCount(),
                response.transcriptStatus(),
                response.dataQuality(),
                response.vadMetrics()
        );
    }

    private String reviewDraft(InterviewAnswerCapture capture) {
        return hasText(capture.getCorrectedTranscript())
                ? capture.getCorrectedTranscript()
                : capture.getRawTranscript();
    }

    private int correctionCount(InterviewAnswerCapture capture) {
        Map<String, Object> metadata = capture.getTranscriptCorrectionJson();
        Object raw = metadata == null ? null : metadata.get("corrections");
        return raw instanceof List<?> corrections ? corrections.size() : 0;
    }

    private InterviewQuestion currentQuestion(UUID sessionId) {
        return questionRepository.findBySessionIdOrderByOrderIndexAsc(sessionId).stream()
                .filter(question -> answerRepository.findBySessionIdAndQuestionId(sessionId, question.getId())
                        .map(answer -> answer.getAnsweredAt() == null).orElse(true))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_OPEN_QUESTION", "Không có câu hỏi đang mở"));
    }

    private InterviewAnswer createDraft(InterviewSession session, InterviewQuestion question) {
        InterviewAnswer answer = new InterviewAnswer();
        answer.setSession(session);
        answer.setQuestionId(question.getId());
        answer.setTranscriptStatus("pending");
        answer.setFeedbackStatus("pending");
        answer.setConversationState("LISTENING");
        return answerRepository.saveAndFlush(answer);
    }

    private SpooledCapture spool(List<MultipartFile> files, List<Integer> sequences, String browserTranscript,
                                 String transcriptionProvider,
                                 UUID captureId, int version, List<Double> durations) throws IOException {
        Path directory = Files.createTempDirectory("sjp-handsfree-");
        MessageDigest digest = sha256();
        digest.update(captureId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update((byte) version);
        digest.update(normalize(browserTranscript).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update(normalize(transcriptionProvider).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (durations != null) {
            durations.forEach(duration -> digest.update(String.valueOf(duration).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        List<SpooledSegment> result = new ArrayList<>();
        try {
            for (int index = 0; index < files.size(); index++) {
                MultipartFile file = files.get(index);
                int sequence = sequences.get(index);
                String mime = file.getContentType() == null ? "audio/webm" : file.getContentType();
                digest.update((byte) sequence);
                digest.update(mime.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                Path target = directory.resolve(String.format(Locale.ROOT, "segment-%04d.bin", sequence));
                try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        digest.update(buffer, 0, read);
                        output.write(buffer, 0, read);
                    }
                }
                result.add(new SpooledSegment(sequence, target, mime));
            }
            result.sort(Comparator.comparingInt(SpooledSegment::sequence));
            return new SpooledCapture(directory, List.copyOf(result), HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | RuntimeException exception) {
            deleteRecursively(directory);
            throw exception;
        }
    }

    private void validateRequest(int version, List<MultipartFile> files, List<Integer> sequences, List<Double> durations) {
        if (version <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "CAPTURE_VERSION_INVALID", "captureVersion phải lớn hơn 0");
        if (files == null || files.isEmpty() || sequences == null || files.size() != sequences.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_SEGMENTS_REQUIRED", "Audio và segmentSequence phải đầy đủ");
        }
        if (durations != null && !durations.isEmpty() && durations.size() != files.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_DURATION_INVALID", "Duration metadata không khớp số segment");
        }
        long totalBytes = 0;
        Set<Integer> seen = new HashSet<>();
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_REQUIRED", "Audio segment bị rỗng");
            validateAudioPart(file);
            totalBytes += file.getSize();
            if (!seen.add(sequences.get(index))) throw new ApiException(HttpStatus.BAD_REQUEST, "SEGMENT_SEQUENCE_INVALID", "segmentSequence bị trùng");
        }
        for (int expected = 0; expected < files.size(); expected++) {
            if (!seen.contains(expected)) throw new ApiException(HttpStatus.BAD_REQUEST, "SEGMENT_SEQUENCE_INVALID", "segmentSequence phải liên tục từ 0");
        }
        if (totalBytes > properties.audioMaxBytes()) throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_TOO_LARGE", "Tổng audio vượt dung lượng cho phép");
        if (durations != null && !durations.isEmpty()) {
            if (durations.stream().anyMatch(duration -> duration == null || duration <= 0 || !Double.isFinite(duration))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_DURATION_INVALID", "Duration metadata không hợp lệ");
            }
            if (durations.stream().mapToDouble(Double::doubleValue).sum() > properties.getAudioMaxSeconds()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_TOO_LONG", "Tổng audio vượt thời lượng cho phép");
            }
        }
    }

    private void validateAudioPart(MultipartFile file) {
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!mime.startsWith("audio/") && !mime.startsWith("video/webm") && !mime.startsWith("application/ogg")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_INVALID_TYPE", "Chỉ hỗ trợ audio WebM, Ogg, WAV, MP3 hoặc MP4");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            boolean wav = header.length >= 4 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F';
            boolean ogg = header.length >= 4 && header[0] == 'O' && header[1] == 'g' && header[2] == 'g' && header[3] == 'S';
            boolean webm = header.length >= 4 && (header[0] & 0xff) == 0x1a && (header[1] & 0xff) == 0x45
                    && (header[2] & 0xff) == 0xdf && (header[3] & 0xff) == 0xa3;
            boolean mp3 = header.length >= 3 && ((header[0] == 'I' && header[1] == 'D' && header[2] == '3')
                    || ((header[0] & 0xff) == 0xff && (header[1] & 0xe0) == 0xe0));
            boolean mp4 = header.length >= 12 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
            if (!wav && !ogg && !webm && !mp3 && !mp4) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_INVALID_CONTENT", "Nội dung segment không phải audio được hỗ trợ");
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_UNREADABLE", "Không thể đọc audio segment");
        }
    }

    private String dataQuality(boolean gladia, boolean vad) {
        if (gladia && vad) return "AUDIO_VAD_PLUS_GLADIA";
        if (gladia) return "AUDIO_GLADIA";
        if (vad) return "BROWSER_PLUS_VAD";
        return "BROWSER_TRANSCRIPT_ONLY";
    }

    private UUID parseUuid(String value, String code) {
        try { return UUID.fromString(value); }
        catch (RuntimeException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, code, "Mã định danh không hợp lệ"); }
    }

    private MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private String normalize(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " "); }

    private String normalizeTranscriptionProvider(String value) {
        return "speechmatics_realtime".equalsIgnoreCase(normalize(value))
                ? "speechmatics_realtime"
                : "web_speech";
    }
    private String emptyToNull(String value) { return hasText(value) ? value.trim() : null; }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private static void deleteRecursively(Path root) {
        if (root == null) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private record Claim(UUID answerId, UUID questionId, HandsFreeAnswerCaptureResponse cachedResponse,
                         GladiaTranscriptionContext context,
                         TranscriptCorrectionContext correctionContext) {}
    private record SpooledSegment(int sequence, Path path, String mimeType) {}
    private record PreparedAudio(DecodedPcmAudio pcm, byte[] wavAudio) {}
    private record SpooledCapture(Path directory, List<SpooledSegment> segments, String payloadHash) implements AutoCloseable {
        @Override public void close() { deleteRecursively(directory); }
    }
}
