package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewAnswerCapture;
import com.sjp.recruitment.model.enums.VoiceEvidenceStatus;
import com.sjp.recruitment.repository.InterviewAnswerCaptureRepository;
import com.sjp.recruitment.service.ai.AiProviderException;
import com.sjp.recruitment.service.ai.GladiaTranscriptionClient;
import com.sjp.recruitment.service.ai.GladiaTranscriptionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GladiaVoiceEvidenceServiceTest {

    private final AiInterviewProperties properties = new AiInterviewProperties();
    private final GladiaTranscriptionClient gladiaClient = mock(GladiaTranscriptionClient.class);
    private final InterviewAnswerCaptureRepository captureRepository = mock(InterviewAnswerCaptureRepository.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final UUID answerId = UUID.randomUUID();
    private final UUID captureId = UUID.randomUUID();
    private InterviewAnswerCapture capture;
    private GladiaVoiceEvidenceService service;

    @BeforeEach
    void setUp() {
        InterviewAnswer answer = new InterviewAnswer();
        answer.setId(answerId);
        capture = new InterviewAnswerCapture();
        capture.setAnswer(answer);
        capture.setCaptureId(captureId);
        capture.setCaptureVersion(1);
        capture.setStatus("completed");
        capture.setVoiceEvidenceStatus(VoiceEvidenceStatus.PENDING);
        capture.setDataQuality("BROWSER_PLUS_VAD");
        capture.setVadMetricsJson(new HashMap<>(java.util.Map.of("status", "completed")));
        when(captureRepository.findByAnswerIdAndCaptureIdAndCaptureVersion(answerId, captureId, 1))
                .thenReturn(Optional.of(capture));
        when(transactions.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            @SuppressWarnings("unchecked") Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        service = new GladiaVoiceEvidenceService(properties, gladiaClient, captureRepository,
                transactions, new SyncTaskExecutor());
    }

    @Test
    void storesGladiaOnlyAsCompletedVoiceEvidence() {
        when(gladiaClient.transcribe(any(byte[].class), anyString(), anyString(), any()))
                .thenReturn("Em dùng Spring Boot.");

        service.submit(answerId, captureId, 1, new byte[]{'R', 'I', 'F', 'F'},
                new GladiaTranscriptionContext(List.of("Spring Boot")));

        assertThat(capture.getGladiaTranscript()).isEqualTo("Em dùng Spring Boot.");
        assertThat(capture.getVoiceEvidenceStatus()).isEqualTo(VoiceEvidenceStatus.COMPLETED);
        assertThat(capture.getDataQuality()).isEqualTo("AUDIO_VAD_PLUS_GLADIA");
        assertThat(capture.getRawTranscript()).isNull();
        verify(captureRepository).save(capture);
    }

    @Test
    void marksProviderFailureWithoutChangingAnswerText() {
        capture.setRawTranscript("Web Speech authoritative");
        when(gladiaClient.transcribe(any(byte[].class), anyString(), anyString(), any()))
                .thenThrow(new AiProviderException("STT_TIMEOUT", "timeout"));

        service.submit(answerId, captureId, 1, new byte[]{'R', 'I', 'F', 'F'},
                GladiaTranscriptionContext.empty());

        assertThat(capture.getVoiceEvidenceStatus()).isEqualTo(VoiceEvidenceStatus.FAILED);
        assertThat(capture.getVoiceEvidenceErrorCode()).isEqualTo("STT_TIMEOUT");
        assertThat(capture.getRawTranscript()).isEqualTo("Web Speech authoritative");
    }

    @Test
    void awaitReturnsFreshCapturesAfterPendingJobsFinish() {
        properties.setVoiceEvidenceAwaitTimeoutMs(0);
        when(captureRepository.findByAnswerIdIn(List.of(answerId))).thenReturn(List.of(capture));

        assertThat(service.awaitAndLoad(List.of(answerId))).containsExactly(capture);
        verify(captureRepository).findByAnswerIdIn(List.of(answerId));
    }
}
