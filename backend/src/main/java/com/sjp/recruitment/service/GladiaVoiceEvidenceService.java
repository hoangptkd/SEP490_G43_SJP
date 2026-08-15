package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.config.AiInterviewVoiceEvidenceConfiguration;
import com.sjp.recruitment.model.entity.InterviewAnswerCapture;
import com.sjp.recruitment.model.enums.VoiceEvidenceStatus;
import com.sjp.recruitment.repository.InterviewAnswerCaptureRepository;
import com.sjp.recruitment.service.ai.AiProviderException;
import com.sjp.recruitment.service.ai.GladiaTranscriptionClient;
import com.sjp.recruitment.service.ai.GladiaTranscriptionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class GladiaVoiceEvidenceService {

    private static final long POLL_INTERVAL_MILLIS = 100;

    private final AiInterviewProperties properties;
    private final GladiaTranscriptionClient gladiaClient;
    private final InterviewAnswerCaptureRepository captureRepository;
    private final TransactionTemplate transactions;
    private final TaskExecutor executor;

    public GladiaVoiceEvidenceService(
            AiInterviewProperties properties,
            GladiaTranscriptionClient gladiaClient,
            InterviewAnswerCaptureRepository captureRepository,
            TransactionTemplate transactions,
            @Qualifier(AiInterviewVoiceEvidenceConfiguration.EXECUTOR_BEAN) TaskExecutor executor) {
        this.properties = properties;
        this.gladiaClient = gladiaClient;
        this.captureRepository = captureRepository;
        this.transactions = transactions;
        this.executor = executor;
    }

    public void submit(UUID answerId, UUID captureId, int captureVersion, byte[] wavAudio,
                       GladiaTranscriptionContext context) {
        VoiceEvidenceJob job = new VoiceEvidenceJob(
                answerId,
                captureId,
                captureVersion,
                wavAudio == null ? new byte[0] : wavAudio.clone(),
                context == null ? GladiaTranscriptionContext.empty() : context
        );
        if (job.wavAudio().length == 0) {
            markFailed(job, "VOICE_EVIDENCE_AUDIO_UNAVAILABLE");
            return;
        }
        try {
            executor.execute(() -> transcribe(job));
        } catch (RuntimeException exception) {
            log.warn("Gladia voice-evidence executor rejected captureId={}, version={}",
                    captureId, captureVersion);
            markFailed(job, "VOICE_EVIDENCE_CAPACITY");
        }
    }

    public List<InterviewAnswerCapture> awaitAndLoad(List<UUID> answerIds) {
        if (answerIds == null || answerIds.isEmpty()) return List.of();
        long timeoutMillis = Math.max(0, properties.getVoiceEvidenceAwaitTimeoutMs());
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (timeoutMillis > 0
                && captureRepository.countByAnswerIdInAndVoiceEvidenceStatus(
                answerIds, VoiceEvidenceStatus.PENDING) > 0
                && System.nanoTime() < deadline) {
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(Math.max(0, deadline - System.nanoTime()));
            try {
                Thread.sleep(Math.min(POLL_INTERVAL_MILLIS, Math.max(1, remainingMillis)));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return captureRepository.findByAnswerIdIn(answerIds);
    }

    private void transcribe(VoiceEvidenceJob job) {
        try {
            String transcript = gladiaClient.transcribe(
                    job.wavAudio(), "answer.wav", "audio/wav", job.context());
            if (transcript == null || transcript.isBlank()) {
                markFailed(job, "STT_EMPTY_TRANSCRIPT");
                return;
            }
            transactions.executeWithoutResult(status -> captureRepository
                    .findByAnswerIdAndCaptureIdAndCaptureVersion(
                            job.answerId(), job.captureId(), job.captureVersion())
                    .ifPresent(capture -> complete(capture, transcript)));
        } catch (AiProviderException exception) {
            markFailed(job, exception.getCode());
        } catch (RuntimeException exception) {
            log.warn("Gladia voice evidence failed: captureId={}, version={}, code={}",
                    job.captureId(), job.captureVersion(), exception.getClass().getSimpleName());
            markFailed(job, "VOICE_EVIDENCE_FAILED");
        }
    }

    private void complete(InterviewAnswerCapture capture, String transcript) {
        if (capture.getVoiceEvidenceStatus() != VoiceEvidenceStatus.PENDING) return;
        capture.setGladiaTranscript(transcript.trim());
        capture.setVoiceEvidenceStatus(VoiceEvidenceStatus.COMPLETED);
        capture.setVoiceEvidenceErrorCode(null);
        capture.setDataQuality(hasCompletedVad(capture) ? "AUDIO_VAD_PLUS_GLADIA" : "AUDIO_GLADIA");
        captureRepository.save(capture);
    }

    private void markFailed(VoiceEvidenceJob job, String errorCode) {
        transactions.executeWithoutResult(status -> captureRepository
                .findByAnswerIdAndCaptureIdAndCaptureVersion(
                        job.answerId(), job.captureId(), job.captureVersion())
                .ifPresent(capture -> {
                    if (capture.getVoiceEvidenceStatus() != VoiceEvidenceStatus.PENDING) return;
                    capture.setVoiceEvidenceStatus(VoiceEvidenceStatus.FAILED);
                    capture.setVoiceEvidenceErrorCode(safeCode(errorCode));
                    captureRepository.save(capture);
                }));
    }

    private boolean hasCompletedVad(InterviewAnswerCapture capture) {
        Map<String, Object> metrics = capture.getVadMetricsJson();
        return metrics != null && "completed".equals(String.valueOf(metrics.get("status")));
    }

    private String safeCode(String errorCode) {
        String value = errorCode == null || errorCode.isBlank() ? "VOICE_EVIDENCE_FAILED" : errorCode.trim();
        return value.length() <= 100 ? value : value.substring(0, 100);
    }

    private record VoiceEvidenceJob(
            UUID answerId,
            UUID captureId,
            int captureVersion,
            byte[] wavAudio,
            GladiaTranscriptionContext context
    ) {
    }
}
