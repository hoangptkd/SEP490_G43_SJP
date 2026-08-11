package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.HandsFreeAnswerCaptureResponse;
import com.sjp.recruitment.model.dto.response.HandsFreeVadMetricsResponse;
import com.sjp.recruitment.model.entity.*;
import com.sjp.recruitment.repository.*;
import com.sjp.recruitment.service.ai.*;
import com.sjp.recruitment.service.audio.AudioCaptureSegment;
import com.sjp.recruitment.service.audio.AudioDecoder;
import com.sjp.recruitment.service.audio.DecodedPcmAudio;
import com.sjp.recruitment.service.audio.PcmWaveWriter;
import com.sjp.recruitment.service.vad.SileroVadAnalyzer;
import com.sjp.recruitment.service.vad.VadAnalysisResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
public class HandsFreeAnswerCaptureService {
    private static final String ANALYSIS_VERSION = "handsfree-vad-gladia-v1";

    private final AiInterviewProperties properties;
    private final CandidateService candidateService;
    private final AiInterviewRateLimiter rateLimiter;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final InterviewAnswerCaptureRepository captureRepository;
    private final TechnicalVocabularyBuilder vocabularyBuilder;
    private final GladiaTranscriptionClient gladiaClient;
    private final AudioDecoder audioDecoder;
    private final PcmWaveWriter waveWriter;
    private final SileroVadAnalyzer vadAnalyzer;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    public HandsFreeAnswerCaptureService(
            AiInterviewProperties properties,
            CandidateService candidateService,
            AiInterviewRateLimiter rateLimiter,
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            InterviewAnswerRepository answerRepository,
            InterviewAnswerCaptureRepository captureRepository,
            TechnicalVocabularyBuilder vocabularyBuilder,
            GladiaTranscriptionClient gladiaClient,
            AudioDecoder audioDecoder,
            PcmWaveWriter waveWriter,
            SileroVadAnalyzer vadAnalyzer,
            TransactionTemplate transactions,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.candidateService = candidateService;
        this.rateLimiter = rateLimiter;
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.captureRepository = captureRepository;
        this.vocabularyBuilder = vocabularyBuilder;
        this.gladiaClient = gladiaClient;
        this.audioDecoder = audioDecoder;
        this.waveWriter = waveWriter;
        this.vadAnalyzer = vadAnalyzer;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
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
        UUID sessionId = parseUuid(sessionIdValue, "SESSION_ID_INVALID");
        UUID questionId = parseUuid(questionIdValue, "QUESTION_ID_INVALID");
        UUID captureId = parseUuid(captureIdValue, "CAPTURE_ID_INVALID");
        if (idempotencyKey == null || !idempotencyKey.equals(captureIdValue)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID", "Idempotency-Key phải trùng captureId");
        }
        validateRequest(captureVersion, audioSegments, segmentSequences, durationSeconds);
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "hands-free-transcribe");

        try (SpooledCapture spool = spool(audioSegments, segmentSequences, browserTranscript, captureId, captureVersion, durationSeconds)) {
            Claim claim = transactions.execute(status -> claim(
                    candidate.getId(), sessionId, questionId, captureId, captureVersion,
                    spool.payloadHash(), browserTranscript));
            if (claim == null) throw new IllegalStateException("Capture claim was not created");
            if (claim.cachedResponse() != null) return claim.cachedResponse();

            GladiaTranscriptionContext context = claim.context();
            PreparedAudio preparedAudio = prepareAudio(spool);
            String gladiaTranscript = preparedAudio.wavPath() == null
                    ? transcribeAll(spool.segments(), context)
                    : transcribeNormalizedAnswer(preparedAudio.wavPath(), context);
            VadAnalysisResult vadResult = preparedAudio.pcm() == null
                    ? analyzeVad(spool.segments()) : analyzeVad(preparedAudio.pcm());
            HandsFreeVadMetricsResponse vadMetrics = HandsFreeVadMetricsResponse.from(vadResult);
            String normalizedBrowser = normalize(browserTranscript);
            String finalTranscript = hasText(gladiaTranscript) ? gladiaTranscript.trim() : normalizedBrowser;
            if (!hasText(finalTranscript)) {
                failCapture(claim.answerId(), captureId, captureVersion, "NO_TRANSCRIPT_AVAILABLE");
                throw new ApiException(HttpStatus.BAD_GATEWAY, "NO_TRANSCRIPT_AVAILABLE",
                        "Không thể tạo transcript từ audio và không có bản ghi nhận realtime để dự phòng");
            }

            String transcriptStatus = hasText(gladiaTranscript) ? "standardized" : "fallback_browser";
            String dataQuality = dataQuality(hasText(gladiaTranscript), vadMetrics != null);
            HandsFreeAnswerCaptureResponse response = new HandsFreeAnswerCaptureResponse(
                    questionId.toString(), captureId.toString(), captureVersion, normalizedBrowser,
                    emptyToNull(gladiaTranscript), finalTranscript, transcriptStatus, dataQuality, vadMetrics);
            return transactions.execute(status -> complete(claim.answerId(), response, vadResult));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_UNREADABLE", "Không thể đọc audio đã tải lên");
        }
    }

    private Claim claim(UUID candidateId, UUID sessionId, UUID questionId, UUID captureId, int captureVersion,
                        String payloadHash, String browserTranscript) {
        InterviewSession session = sessionRepository.findOwnedForUpdate(sessionId, candidateId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Không tìm thấy phiên phỏng vấn"));
        if (session.isCompleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_COMPLETED", "Phiên phỏng vấn đã hoàn thành");
        }
        InterviewQuestion requested = questionRepository.findByIdAndSessionId(questionId, sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "Không tìm thấy câu hỏi"));
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
                    return new Claim(existingAnswer.get().getId(), toResponse(requested.getId(), capture), GladiaTranscriptionContext.empty());
                }
                throw new ApiException(HttpStatus.CONFLICT, "CAPTURE_PROCESSING", "Audio đang được chuẩn hóa");
            }
        }
        InterviewQuestion current = currentQuestion(sessionId);
        if (!current.getId().equals(requested.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_QUESTION", "Audio không thuộc câu hỏi hiện tại");
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
        capture.setCaptureId(captureId);
        capture.setCaptureVersion(captureVersion);
        capture.setPayloadHash(payloadHash);
        capture.setBrowserTranscript(normalize(browserTranscript));
        captureRepository.saveAndFlush(capture);
        answer.setActiveCaptureId(captureId);
        answer.setActiveCaptureVersion(captureVersion);
        answer.setTranscriptStatus("processing");
        answer.setErrorMessage(null);
        answerRepository.save(answer);
        return new Claim(answer.getId(), null, vocabularyBuilder.build(session, requested));
    }

    private HandsFreeAnswerCaptureResponse complete(
            UUID answerId, HandsFreeAnswerCaptureResponse response, VadAnalysisResult vadResult) {
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
        capture.setFinalTranscript(response.finalTranscript());
        capture.setTranscriptStatus(response.transcriptStatus());
        capture.setDataQuality(response.dataQuality());
        capture.setVadMetricsJson(response.vadMetrics() == null
                ? Map.of() : objectMapper.convertValue(response.vadMetrics(), Map.class));
        captureRepository.save(capture);

        if (hasText(response.gladiaTranscript())) answer.setOriginalSpeechTranscript(response.gladiaTranscript());
        answer.setSpeechAnalysisJson(analysisJson(response, vadResult));
        answer.setTranscriptStatus("completed");
        if (vadResult != null && vadResult.audioDurationSeconds() > 0) {
            answer.setDurationSeconds(Math.max(1, (int) Math.ceil(vadResult.audioDurationSeconds())));
        }
        answer.setErrorMessage(hasText(response.gladiaTranscript()) ? null : "GLADIA_UNAVAILABLE_BROWSER_FALLBACK");
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
                    answerRepository.save(answer);
                }));
    }

    private String transcribeAll(List<SpooledSegment> segments, GladiaTranscriptionContext context) {
        List<String> transcripts = new ArrayList<>();
        try {
            for (SpooledSegment segment : segments) {
                String transcript = gladiaClient.transcribe(segment.path(), segment.mimeType(), context);
                if (hasText(transcript)) transcripts.add(transcript.trim());
            }
            return String.join(" ", transcripts).replaceAll("\\s+", " ").trim();
        } catch (AiProviderException exception) {
            return null;
        }
    }

    private String transcribeNormalizedAnswer(Path wavPath, GladiaTranscriptionContext context) {
        try {
            return gladiaClient.transcribe(wavPath, "audio/wav", context);
        } catch (AiProviderException exception) {
            return null;
        }
    }

    private PreparedAudio prepareAudio(SpooledCapture spool) {
        try {
            DecodedPcmAudio pcm = audioDecoder.decode(spool.segments().stream()
                    .map(segment -> AudioCaptureSegment.fromPath(segment.sequence(), segment.path(), segment.mimeType()))
                    .toList());
            Path wav = waveWriter.write(pcm, spool.directory().resolve("normalized-answer.wav"));
            return new PreparedAudio(pcm, wav);
        } catch (RuntimeException exception) {
            return new PreparedAudio(null, null);
        }
    }

    private VadAnalysisResult analyzeVad(List<SpooledSegment> segments) {
        try {
            return vadAnalyzer.analyze(segments.stream()
                    .map(segment -> AudioCaptureSegment.fromPath(segment.sequence(), segment.path(), segment.mimeType()))
                    .toList());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private VadAnalysisResult analyzeVad(DecodedPcmAudio pcm) {
        try {
            return vadAnalyzer.analyze(pcm);
        } catch (RuntimeException exception) {
            return null;
        }
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
                capture.getFinalTranscript(), capture.getTranscriptStatus(), capture.getDataQuality(), metrics);
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
        return answerRepository.saveAndFlush(answer);
    }

    private SpooledCapture spool(List<MultipartFile> files, List<Integer> sequences, String browserTranscript,
                                 UUID captureId, int version, List<Double> durations) throws IOException {
        Path directory = Files.createTempDirectory("sjp-handsfree-");
        MessageDigest digest = sha256();
        digest.update(captureId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update((byte) version);
        digest.update(normalize(browserTranscript).getBytes(java.nio.charset.StandardCharsets.UTF_8));
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

    private record Claim(UUID answerId, HandsFreeAnswerCaptureResponse cachedResponse,
                         GladiaTranscriptionContext context) {}
    private record SpooledSegment(int sequence, Path path, String mimeType) {}
    private record PreparedAudio(DecodedPcmAudio pcm, Path wavPath) {}
    private record SpooledCapture(Path directory, List<SpooledSegment> segments, String payloadHash) implements AutoCloseable {
        @Override public void close() { deleteRecursively(directory); }
    }
}
