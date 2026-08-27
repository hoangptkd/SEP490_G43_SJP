package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.config.AiInterviewBackgroundConfiguration;
import com.sjp.recruitment.model.entity.InterviewAnswerCapture;
import com.sjp.recruitment.model.enums.TranscriptCorrectionStatus;
import com.sjp.recruitment.repository.InterviewAnswerCaptureRepository;
import com.sjp.recruitment.service.ai.AiProviderException;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import com.sjp.recruitment.service.ai.TranscriptCorrectionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
public class TranscriptCorrectionService {

    private static final double MINIMUM_SAFE_CONFIDENCE = 0.90;
    private static final int MAX_ATTEMPTS = 2;
    private static final String UNDECLARED_CHANGE_IGNORED_CODE =
            "TRANSCRIPT_CORRECTION_UNDECLARED_CHANGE_IGNORED";

    private final AiInterviewProperties properties;
    private final ShopAiKeyClient aiClient;
    private final InterviewAnswerCaptureRepository captureRepository;
    private final TransactionTemplate transactions;
    private final TaskExecutor executor;
    private final ConcurrentMap<String, Boolean> inFlight = new ConcurrentHashMap<>();

    public TranscriptCorrectionService(
            AiInterviewProperties properties,
            ShopAiKeyClient aiClient,
            InterviewAnswerCaptureRepository captureRepository,
            TransactionTemplate transactions,
            @Qualifier(AiInterviewBackgroundConfiguration.EXECUTOR_BEAN) TaskExecutor executor
    ) {
        this.properties = properties;
        this.aiClient = aiClient;
        this.captureRepository = captureRepository;
        this.transactions = transactions;
        this.executor = executor;
    }

    public void submit(
            UUID sessionId,
            UUID answerId,
            UUID captureId,
            int captureVersion,
            TranscriptCorrectionContext context
    ) {
        String jobKey = answerId + ":" + captureId + ":" + captureVersion;
        if (inFlight.putIfAbsent(jobKey, Boolean.TRUE) != null) return;
        try {
            executor.execute(() -> {
                try {
                    correct(sessionId, answerId, captureId, captureVersion, context);
                } finally {
                    inFlight.remove(jobKey);
                }
            });
        } catch (RuntimeException exception) {
            inFlight.remove(jobKey);
            log.warn("Transcript correction executor rejected captureId={}, version={}",
                    captureId, captureVersion);
            persist(answerId, captureId, captureVersion,
                    normalize(context == null ? null : context.rawTranscript()),
                    TranscriptCorrectionStatus.FAILED, List.of(), Map.of(),
                    "TRANSCRIPT_CORRECTION_CAPACITY");
        }
    }

    public CorrectionResult correct(
            UUID sessionId,
            UUID answerId,
            UUID captureId,
            int captureVersion,
            TranscriptCorrectionContext context
    ) {
        String rawTranscript = normalize(context == null ? null : context.rawTranscript());
        if (rawTranscript.isBlank()) {
            return new CorrectionResult(rawTranscript, TranscriptCorrectionStatus.NOT_REQUIRED, 0);
        }
        Attempt attempt = transactions.execute(status -> beginAttempt(
                answerId, captureId, captureVersion, rawTranscript));
        if (attempt == null) {
            return new CorrectionResult(rawTranscript, TranscriptCorrectionStatus.FAILED, 0);
        }
        if (attempt.completedResult() != null) return attempt.completedResult();
        if (!properties.isTranscriptCorrectionEnabled()) {
            return persist(answerId, captureId, captureVersion, rawTranscript,
                    TranscriptCorrectionStatus.NOT_REQUIRED, List.of(), Map.of(), null);
        }
        if (!attempt.allowed()) {
            return persist(answerId, captureId, captureVersion, rawTranscript,
                    TranscriptCorrectionStatus.FAILED, List.of(), Map.of(),
                    "TRANSCRIPT_CORRECTION_INTERRUPTED");
        }

        try {
            ShopAiKeyClient.TranscriptCorrectionDraft draft =
                    aiClient.correctBrowserTranscript(sessionId, context);
            ValidatedCorrection validated = validate(rawTranscript, draft);
            if (validated.mismatchDiagnostics() != null) {
                logIgnoredUndeclaredChanges(captureId, captureVersion, validated.mismatchDiagnostics());
            }
            Map<String, Object> metadata = correctionMetadata(draft, validated, attempt.attemptCount());
            TranscriptCorrectionStatus resultStatus = validated.acceptedCorrections().isEmpty()
                    ? TranscriptCorrectionStatus.UNCHANGED
                    : TranscriptCorrectionStatus.CORRECTED;
            return persist(answerId, captureId, captureVersion, validated.correctedTranscript(),
                    resultStatus, validated.acceptedCorrections(), metadata, null);
        } catch (InvalidCorrectionException exception) {
            log.warn("Transcript correction rejected: captureId={}, version={}, code={}",
                    captureId, captureVersion, exception.code());
            return persist(answerId, captureId, captureVersion, rawTranscript,
                    TranscriptCorrectionStatus.FAILED, List.of(),
                    Map.of("attemptCount", attempt.attemptCount()), exception.code());
        } catch (AiProviderException exception) {
            log.warn("Transcript correction provider failed: captureId={}, version={}, code={}",
                    captureId, captureVersion, exception.getCode());
            return persist(answerId, captureId, captureVersion, rawTranscript,
                    TranscriptCorrectionStatus.FAILED, List.of(),
                    Map.of("attemptCount", attempt.attemptCount()), exception.getCode());
        } catch (RuntimeException exception) {
            log.warn("Transcript correction failed: captureId={}, version={}, code={}",
                    captureId, captureVersion, exception.getClass().getSimpleName());
            return persist(answerId, captureId, captureVersion, rawTranscript,
                    TranscriptCorrectionStatus.FAILED, List.of(),
                    Map.of("attemptCount", attempt.attemptCount()), "TRANSCRIPT_CORRECTION_FAILED");
        }
    }

    private Attempt beginAttempt(UUID answerId, UUID captureId, int captureVersion, String rawTranscript) {
        InterviewAnswerCapture capture = captureRepository
                .findForUpdate(answerId, captureId, captureVersion)
                .orElse(null);
        if (capture == null) return null;
        TranscriptCorrectionStatus current = capture.getTranscriptCorrectionStatus();
        if (current != TranscriptCorrectionStatus.PENDING) {
            return new Attempt(false, attemptCount(capture), result(capture, rawTranscript));
        }
        int attemptCount = attemptCount(capture) + 1;
        if (attemptCount > MAX_ATTEMPTS) {
            return new Attempt(false, attemptCount, null);
        }
        Map<String, Object> metadata = new LinkedHashMap<>(safeMap(capture.getTranscriptCorrectionJson()));
        metadata.put("attemptCount", attemptCount);
        capture.setTranscriptCorrectionJson(metadata);
        captureRepository.save(capture);
        return new Attempt(true, attemptCount, null);
    }

    private ValidatedCorrection validate(
            String rawTranscript,
            ShopAiKeyClient.TranscriptCorrectionDraft draft
    ) {
        if (draft == null) throw invalid("TRANSCRIPT_CORRECTION_EMPTY");
        String working = rawTranscript;
        List<AcceptedCorrection> accepted = new ArrayList<>();
        List<RejectedCorrection> rejected = new ArrayList<>();
        double threshold = Math.max(MINIMUM_SAFE_CONFIDENCE,
                properties.getTranscriptCorrectionMinConfidence());
        for (int itemIndex = 0; itemIndex < draft.corrections().size(); itemIndex++) {
            ShopAiKeyClient.TranscriptCorrectionItemDraft item = draft.corrections().get(itemIndex);
            String original = normalize(item.original());
            String replacement = normalize(item.replacement());
            if (item.confidence() < threshold) {
                rejected.add(rejected(itemIndex, item, original, replacement, "LOW_CONFIDENCE"));
                continue;
            }
            if (original.isBlank() || replacement.isBlank()) {
                rejected.add(rejected(itemIndex, item, original, replacement, "EMPTY_TEXT"));
                continue;
            }
            if (original.equals(replacement)) {
                rejected.add(rejected(itemIndex, item, original, replacement, "NO_EFFECT"));
                continue;
            }
            int occurrenceCount = occurrences(working, original);
            if (occurrenceCount == 0) {
                rejected.add(rejected(itemIndex, item, original, replacement, "ORIGINAL_NOT_FOUND"));
                continue;
            }
            if (occurrenceCount > 1) {
                rejected.add(rejected(itemIndex, item, original, replacement, "AMBIGUOUS_OCCURRENCE"));
                continue;
            }
            int maximumReplacementLength = Math.max(original.length() * 3, original.length() + 20);
            if (replacement.length() > maximumReplacementLength) {
                rejected.add(rejected(itemIndex, item, original, replacement, "REPLACEMENT_TOO_LONG"));
                continue;
            }
            int index = working.indexOf(original);
            working = working.substring(0, index) + replacement + working.substring(index + original.length());
            accepted.add(new AcceptedCorrection(
                    original, replacement, item.confidence(), item.reason()));
        }
        working = normalize(working);
        String providerCorrectedTranscript = normalize(draft.correctedTranscript());
        MismatchDiagnostics mismatchDiagnostics = null;
        if (!working.equals(providerCorrectedTranscript)) {
            mismatchDiagnostics = new MismatchDiagnostics(
                    rawTranscript,
                    providerCorrectedTranscript,
                    working,
                    firstMismatchIndex(working, providerCorrectedTranscript),
                    List.copyOf(draft.corrections()),
                    List.copyOf(accepted),
                    List.copyOf(rejected)
            );
        }
        return new ValidatedCorrection(
                working,
                List.copyOf(accepted),
                List.copyOf(rejected),
                mismatchDiagnostics
        );
    }

    private RejectedCorrection rejected(
            int itemIndex,
            ShopAiKeyClient.TranscriptCorrectionItemDraft item,
            String original,
            String replacement,
            String rejectionReason
    ) {
        return new RejectedCorrection(
                itemIndex,
                original,
                replacement,
                item.confidence(),
                item.reason(),
                rejectionReason
        );
    }

    private int firstMismatchIndex(String backendTranscript, String providerTranscript) {
        int sharedLength = Math.min(backendTranscript.length(), providerTranscript.length());
        for (int index = 0; index < sharedLength; index++) {
            if (backendTranscript.charAt(index) != providerTranscript.charAt(index)) return index;
        }
        return backendTranscript.length() == providerTranscript.length() ? -1 : sharedLength;
    }

    private void logIgnoredUndeclaredChanges(
            UUID captureId,
            int captureVersion,
            MismatchDiagnostics diagnostics
    ) {
        log.warn("Transcript correction provider changes ignored: captureId={}, version={}, code={}, "
                        + "mismatchIndex={}, rawTranscript=\"{}\", providerCorrectedTranscript=\"{}\", "
                        + "backendReconstructedTranscript=\"{}\", declaredCorrections={}, "
                        + "acceptedCorrections={}, rejectedCorrections={}",
                captureId,
                captureVersion,
                UNDECLARED_CHANGE_IGNORED_CODE,
                diagnostics.mismatchIndex(),
                diagnostics.rawTranscript(),
                diagnostics.providerCorrectedTranscript(),
                diagnostics.backendReconstructedTranscript(),
                diagnostics.declaredCorrections(),
                diagnostics.acceptedCorrections(),
                diagnostics.rejectedCorrections());
    }

    private CorrectionResult persist(
            UUID answerId,
            UUID captureId,
            int captureVersion,
            String safeDraft,
            TranscriptCorrectionStatus status,
            List<AcceptedCorrection> acceptedCorrections,
            Map<String, Object> metadata,
            String errorCode
    ) {
        CorrectionResult result = transactions.execute(transactionStatus -> {
            InterviewAnswerCapture capture = captureRepository
                    .findForUpdate(answerId, captureId, captureVersion)
                    .orElse(null);
            if (capture == null) {
                return new CorrectionResult(safeDraft, TranscriptCorrectionStatus.FAILED, 0);
            }
            capture.setCorrectedTranscript(safeDraft);
            capture.setTranscriptCorrectionStatus(status);
            capture.setTranscriptCorrectionErrorCode(safeCode(errorCode));
            Map<String, Object> mergedMetadata = new LinkedHashMap<>(
                    safeMap(capture.getTranscriptCorrectionJson()));
            if (metadata != null) mergedMetadata.putAll(metadata);
            capture.setTranscriptCorrectionJson(mergedMetadata);
            captureRepository.save(capture);

            return new CorrectionResult(safeDraft, status, acceptedCorrections.size());
        });
        return result == null
                ? new CorrectionResult(safeDraft, TranscriptCorrectionStatus.FAILED, 0)
                : result;
    }

    private CorrectionResult result(InterviewAnswerCapture capture, String rawTranscript) {
        String draft = normalize(capture.getCorrectedTranscript());
        if (draft.isBlank()) draft = rawTranscript;
        return new CorrectionResult(draft, capture.getTranscriptCorrectionStatus(),
                acceptedCorrectionCount(capture));
    }

    private Map<String, Object> correctionMetadata(
            ShopAiKeyClient.TranscriptCorrectionDraft draft,
            ValidatedCorrection validated,
            int attemptCount
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("attemptCount", attemptCount);
        metadata.put("providerCorrectedTranscript", normalize(draft.correctedTranscript()));
        metadata.put("corrections", validated.acceptedCorrections().stream().map(item -> Map.of(
                "original", item.original(),
                "replacement", item.replacement(),
                "confidence", item.confidence(),
                "reason", item.reason()
        )).toList());
        metadata.put("evidence", draft.evidence().stream().map(item -> Map.of(
                "type", item.type(), "text", item.text())).toList());
        metadata.put("answerSummary", draft.answerSummary());
        metadata.put("followUpNeeded", draft.followUpNeeded());
        metadata.put("followUpReason", draft.followUpReason() == null ? "" : draft.followUpReason());
        metadata.put("promptVersion", properties.getTranscriptCorrectionPromptVersion());
        metadata.put("providerTranscriptMatched", validated.mismatchDiagnostics() == null);
        metadata.put("rejectedCorrections", validated.rejectedCorrections().stream().map(item -> Map.of(
                "itemIndex", item.itemIndex(),
                "original", item.original(),
                "replacement", item.replacement(),
                "confidence", item.confidence(),
                "reason", item.reason(),
                "rejectionReason", item.rejectionReason()
        )).toList());
        if (validated.mismatchDiagnostics() != null) {
            metadata.put("validationWarningCode", UNDECLARED_CHANGE_IGNORED_CODE);
            metadata.put("providerMismatchIndex", validated.mismatchDiagnostics().mismatchIndex());
        }
        return metadata;
    }

    private int acceptedCorrectionCount(InterviewAnswerCapture capture) {
        Object raw = safeMap(capture.getTranscriptCorrectionJson()).get("corrections");
        return raw instanceof List<?> values ? values.size() : 0;
    }

    private int attemptCount(InterviewAnswerCapture capture) {
        Object raw = safeMap(capture.getTranscriptCorrectionJson()).get("attemptCount");
        return raw instanceof Number value ? Math.max(0, value.intValue()) : 0;
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private int occurrences(String text, String search) {
        int count = 0;
        int from = 0;
        while (from <= text.length() - search.length()) {
            int index = text.indexOf(search, from);
            if (index < 0) break;
            count++;
            from = index + search.length();
        }
        return count;
    }

    private String safeCode(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replaceAll("[^A-Za-z0-9_]+", "_");
        return normalized.substring(0, Math.min(normalized.length(), 100));
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private InvalidCorrectionException invalid(String code) {
        return new InvalidCorrectionException(code);
    }

    public record CorrectionResult(
            String correctedTranscript,
            TranscriptCorrectionStatus status,
            int correctionCount
    ) {
    }

    public record AcceptedCorrection(
            String original,
            String replacement,
            double confidence,
            String reason
    ) {
    }

    public record RejectedCorrection(
            int itemIndex,
            String original,
            String replacement,
            double confidence,
            String reason,
            String rejectionReason
    ) {
    }

    private record Attempt(boolean allowed, int attemptCount, CorrectionResult completedResult) {
    }

    private record ValidatedCorrection(
            String correctedTranscript,
            List<AcceptedCorrection> acceptedCorrections,
            List<RejectedCorrection> rejectedCorrections,
            MismatchDiagnostics mismatchDiagnostics
    ) {
    }

    private record MismatchDiagnostics(
            String rawTranscript,
            String providerCorrectedTranscript,
            String backendReconstructedTranscript,
            int mismatchIndex,
            List<ShopAiKeyClient.TranscriptCorrectionItemDraft> declaredCorrections,
            List<AcceptedCorrection> acceptedCorrections,
            List<RejectedCorrection> rejectedCorrections
    ) {
    }

    private static final class InvalidCorrectionException extends RuntimeException {
        private final String code;

        private InvalidCorrectionException(String code) {
            super(code);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }
}
