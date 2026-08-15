package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewAnswerCapture;
import com.sjp.recruitment.model.entity.InterviewConversationTurn;
import com.sjp.recruitment.model.enums.InterviewTurnAnswerStatus;
import com.sjp.recruitment.model.enums.TranscriptCorrectionStatus;
import com.sjp.recruitment.repository.InterviewAnswerCaptureRepository;
import com.sjp.recruitment.service.ai.AiProviderException;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import com.sjp.recruitment.service.ai.TranscriptCorrectionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class TranscriptCorrectionServiceTest {

    @Mock private ShopAiKeyClient aiClient;
    @Mock private InterviewAnswerCaptureRepository captureRepository;
    @Mock private TransactionTemplate transactions;

    private final AiInterviewProperties properties = new AiInterviewProperties();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID answerId = UUID.randomUUID();
    private final UUID captureId = UUID.randomUUID();
    private InterviewAnswer answer;
    private InterviewConversationTurn turn;
    private InterviewAnswerCapture capture;
    private TranscriptCorrectionService service;

    @BeforeEach
    void setUp() {
        answer = new InterviewAnswer();
        answer.setId(answerId);
        answer.setActiveCaptureId(captureId);
        answer.setActiveCaptureVersion(1);
        answer.setRawTranscript("ờ em dùng spring bút với post men");
        answer.setFinalTranscript(answer.getRawTranscript());
        answer.setTranscriptText(answer.getRawTranscript());

        turn = new InterviewConversationTurn();
        turn.setId(UUID.randomUUID());
        turn.setAnswerStatus(InterviewTurnAnswerStatus.REVIEWING);
        turn.setCandidateRawAnswer(answer.getRawTranscript());
        turn.setCandidateFinalAnswer(answer.getRawTranscript());

        capture = new InterviewAnswerCapture();
        capture.setId(UUID.randomUUID());
        capture.setAnswer(answer);
        capture.setConversationTurn(turn);
        capture.setCaptureId(captureId);
        capture.setCaptureVersion(1);
        capture.setRawTranscript(answer.getRawTranscript());
        capture.setTranscriptCorrectionStatus(TranscriptCorrectionStatus.PENDING);

        when(captureRepository.findByAnswerIdAndCaptureIdAndCaptureVersion(answerId, captureId, 1))
                .thenReturn(Optional.of(capture));
        when(transactions.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        });
        service = new TranscriptCorrectionService(properties, aiClient, captureRepository, transactions);
    }

    @Test
    void appliesOnlyDeclaredHighConfidenceCorrectionsToTheActiveReviewDraft() {
        when(aiClient.correctBrowserTranscript(sessionId, context())).thenReturn(draft(
                "ờ em dùng Spring Boot với Postman",
                List.of(
                        correction("spring bút", "Spring Boot", 0.98, "cv_term"),
                        correction("post men", "Postman", 0.97, "current_question")
                )));

        TranscriptCorrectionService.CorrectionResult result = correct();

        assertThat(result.status()).isEqualTo(TranscriptCorrectionStatus.CORRECTED);
        assertThat(result.correctedTranscript()).isEqualTo("ờ em dùng Spring Boot với Postman");
        assertThat(result.correctionCount()).isEqualTo(2);
        assertThat(capture.getRawTranscript()).isEqualTo("ờ em dùng spring bút với post men");
        assertThat(capture.getCorrectedTranscript()).isEqualTo(result.correctedTranscript());
        assertThat(capture.getTranscriptCorrectionJson()).containsKeys(
                "corrections", "evidence", "answerSummary", "followUpNeeded", "promptVersion");
        assertThat(answer.getFinalTranscript()).isEqualTo(result.correctedTranscript());
        assertThat(turn.getCandidateFinalAnswer()).isEqualTo(result.correctedTranscript());
    }

    @Test
    void ignoresLowConfidenceCorrectionWhenProviderLeavesTranscriptUnchanged() {
        properties.setTranscriptCorrectionMinConfidence(0.50);
        when(aiClient.correctBrowserTranscript(sessionId, context())).thenReturn(draft(
                answer.getRawTranscript(),
                List.of(correction("spring bút", "Spring Boot", 0.89, "cv_term"))));

        TranscriptCorrectionService.CorrectionResult result = correct();

        assertThat(result.status()).isEqualTo(TranscriptCorrectionStatus.UNCHANGED);
        assertThat(result.correctedTranscript()).isEqualTo(answer.getRawTranscript());
        assertThat(result.correctionCount()).isZero();
    }

    @Test
    void ignoresAmbiguousDuplicateSourceText() {
        answer.setRawTranscript("bug rồi lại bug");
        capture.setRawTranscript(answer.getRawTranscript());
        when(aiClient.correctBrowserTranscript(sessionId, context())).thenReturn(draft(
                answer.getRawTranscript(),
                List.of(correction("bug", "BUG", 0.99, "technical_vocabulary"))));

        TranscriptCorrectionService.CorrectionResult result = correct();

        assertThat(result.status()).isEqualTo(TranscriptCorrectionStatus.UNCHANGED);
        assertThat(result.correctedTranscript()).isEqualTo("bug rồi lại bug");
    }

    @Test
    void ignoresUndeclaredProviderEditsAndAppliesOnlyDeclaredCorrections(CapturedOutput output) {
        when(aiClient.correctBrowserTranscript(sessionId, context())).thenReturn(draft(
                "Em đã sử dụng Spring Boot và Postman rất thành thạo.",
                List.of(correction("spring bút", "Spring Boot", 0.98, "cv_term"))));

        TranscriptCorrectionService.CorrectionResult result = correct();

        assertThat(result.status()).isEqualTo(TranscriptCorrectionStatus.CORRECTED);
        assertThat(result.correctedTranscript()).isEqualTo("ờ em dùng Spring Boot với post men");
        assertThat(capture.getTranscriptCorrectionErrorCode()).isNull();
        assertThat(capture.getTranscriptCorrectionJson())
                .containsEntry("providerTranscriptMatched", false)
                .containsEntry("validationWarningCode", "TRANSCRIPT_CORRECTION_UNDECLARED_CHANGE_IGNORED")
                .containsEntry("providerMismatchIndex", 0);
        assertThat(answer.getFinalTranscript()).isEqualTo(result.correctedTranscript());
        assertThat(output.getAll())
                .contains("Transcript correction provider changes ignored")
                .contains("code=TRANSCRIPT_CORRECTION_UNDECLARED_CHANGE_IGNORED")
                .contains("mismatchIndex=0")
                .contains("rawTranscript=\"")
                .contains("spring b")
                .contains("providerCorrectedTranscript=\"Em")
                .contains("Spring Boot")
                .contains("Postman")
                .contains("backendReconstructedTranscript=\"")
                .contains("declaredCorrections=[TranscriptCorrectionItemDraft")
                .contains("acceptedCorrections=[AcceptedCorrection")
                .contains("rejectedCorrections=[]");
    }

    @Test
    void ignoresProviderEditWhenItsDeclaredCorrectionIsAmbiguous(CapturedOutput output) {
        answer.setRawTranscript("bug rồi lại bug");
        capture.setRawTranscript(answer.getRawTranscript());
        when(aiClient.correctBrowserTranscript(sessionId, context())).thenReturn(draft(
                "BUG rồi lại bug",
                List.of(correction("bug", "BUG", 0.99, "technical_vocabulary"))));

        TranscriptCorrectionService.CorrectionResult result = correct();

        assertThat(result.status()).isEqualTo(TranscriptCorrectionStatus.UNCHANGED);
        assertThat(result.correctedTranscript()).isEqualTo("bug rồi lại bug");
        assertThat(capture.getTranscriptCorrectionErrorCode()).isNull();
        assertThat(output.getAll())
                .contains("rawTranscript=\"bug")
                .contains("providerCorrectedTranscript=\"BUG")
                .contains("rejectionReason=AMBIGUOUS_OCCURRENCE");
    }

    @Test
    void keepsSafeReconstructionWhenProviderAddsUndeclaredCapitalizationChanges(CapturedOutput output) {
        String raw = "trong thời gian thực tập tại Aloha tôi tham gia phát triển và bảo trì hệ thống cim "
                + "và telesale Call Center bằng Java springfood spring mvc và với vai trò Bách aninton "
                + "Tôi phụ trách xây dựng và tối ưu Red full api và xử lý các lỗi backgen";
        answer.setRawTranscript(raw);
        answer.setFinalTranscript(raw);
        answer.setTranscriptText(raw);
        capture.setRawTranscript(raw);
        turn.setCandidateRawAnswer(raw);
        turn.setCandidateFinalAnswer(raw);
        when(aiClient.correctBrowserTranscript(sessionId, context())).thenReturn(draft(
                "trong thời gian thực tập tại Aloha tôi tham gia phát triển và bảo trì hệ thống CIM "
                        + "và telesale Call Center bằng Java Spring Boot Spring MVC và với vai trò Backend Engineer "
                        + "tôi phụ trách xây dựng và tối ưu RESTful API và xử lý các lỗi backend",
                List.of(
                        correction("cim", "CIM", 0.95, "technical_vocabulary"),
                        correction("springfood", "Spring Boot", 0.98, "cv_term"),
                        correction("Bách aninton", "Backend Engineer", 0.92, "context"),
                        correction("Red full api", "RESTful API", 0.97, "cv_term"),
                        correction("backgen", "backend", 0.95, "context")
                )));

        TranscriptCorrectionService.CorrectionResult result = correct();

        assertThat(result.status()).isEqualTo(TranscriptCorrectionStatus.CORRECTED);
        assertThat(result.correctionCount()).isEqualTo(5);
        assertThat(result.correctedTranscript()).isEqualTo(
                "trong thời gian thực tập tại Aloha tôi tham gia phát triển và bảo trì hệ thống CIM "
                        + "và telesale Call Center bằng Java Spring Boot spring mvc và với vai trò Backend Engineer "
                        + "Tôi phụ trách xây dựng và tối ưu RESTful API và xử lý các lỗi backend");
        assertThat(capture.getTranscriptCorrectionErrorCode()).isNull();
        assertThat(capture.getTranscriptCorrectionJson())
                .containsEntry("providerTranscriptMatched", false)
                .containsEntry("validationWarningCode", "TRANSCRIPT_CORRECTION_UNDECLARED_CHANGE_IGNORED")
                .containsEntry("providerMismatchIndex", 129);
        assertThat(output.getAll())
                .contains("code=TRANSCRIPT_CORRECTION_UNDECLARED_CHANGE_IGNORED")
                .contains("mismatchIndex=129")
                .contains("backendReconstructedTranscript=\"");
    }

    @Test
    void providerFailureDoesNotBlockReviewAndKeepsRawTranscript() {
        when(aiClient.correctBrowserTranscript(sessionId, context()))
                .thenThrow(new AiProviderException("AI_PROVIDER_TIMEOUT", "timeout"));

        TranscriptCorrectionService.CorrectionResult result = correct();

        assertThat(result.status()).isEqualTo(TranscriptCorrectionStatus.FAILED);
        assertThat(result.correctedTranscript()).isEqualTo(answer.getRawTranscript());
        assertThat(capture.getTranscriptCorrectionErrorCode()).isEqualTo("AI_PROVIDER_TIMEOUT");
        assertThat(turn.getCandidateFinalAnswer()).isEqualTo(answer.getRawTranscript());
    }

    @Test
    void staleCaptureStoresItsOwnResultButCannotOverwriteNewerReviewDraft() {
        UUID newerCaptureId = UUID.randomUUID();
        answer.setActiveCaptureId(newerCaptureId);
        answer.setActiveCaptureVersion(2);
        answer.setFinalTranscript("bản nháp mới hơn");
        answer.setTranscriptText("bản nháp mới hơn");
        turn.setCandidateFinalAnswer("bản nháp mới hơn");
        when(aiClient.correctBrowserTranscript(sessionId, context())).thenReturn(draft(
                "ờ em dùng Spring Boot với post men",
                List.of(correction("spring bút", "Spring Boot", 0.98, "cv_term"))));

        TranscriptCorrectionService.CorrectionResult result = correct();

        assertThat(result.status()).isEqualTo(TranscriptCorrectionStatus.CORRECTED);
        assertThat(capture.getCorrectedTranscript()).isEqualTo("ờ em dùng Spring Boot với post men");
        assertThat(answer.getFinalTranscript()).isEqualTo("bản nháp mới hơn");
        assertThat(turn.getCandidateFinalAnswer()).isEqualTo("bản nháp mới hơn");
    }

    @Test
    void completedTurnCannotBeOverwrittenByLateCorrectionResult() {
        turn.setAnswerStatus(InterviewTurnAnswerStatus.CONFIRMED);
        turn.setCandidateFinalAnswer("bản ứng viên đã xác nhận");
        answer.setFinalTranscript("bản ứng viên đã xác nhận");
        answer.setTranscriptText("bản ứng viên đã xác nhận");
        when(aiClient.correctBrowserTranscript(sessionId, context())).thenReturn(draft(
                "ờ em dùng Spring Boot với post men",
                List.of(correction("spring bút", "Spring Boot", 0.98, "cv_term"))));

        TranscriptCorrectionService.CorrectionResult result = correct();

        assertThat(result.status()).isEqualTo(TranscriptCorrectionStatus.CORRECTED);
        assertThat(capture.getCorrectedTranscript()).isEqualTo("ờ em dùng Spring Boot với post men");
        assertThat(answer.getFinalTranscript()).isEqualTo("bản ứng viên đã xác nhận");
        assertThat(turn.getCandidateFinalAnswer()).isEqualTo("bản ứng viên đã xác nhận");
    }

    @Test
    void idempotentRetryReturnsPersistedCorrectionWithoutCallingProviderAgain() {
        capture.setTranscriptCorrectionStatus(TranscriptCorrectionStatus.CORRECTED);
        capture.setCorrectedTranscript("ờ em dùng Spring Boot với Postman");
        capture.setTranscriptCorrectionJson(Map.of("corrections", List.of(Map.of("original", "spring bút"))));

        TranscriptCorrectionService.CorrectionResult result = correct();

        assertThat(result.status()).isEqualTo(TranscriptCorrectionStatus.CORRECTED);
        assertThat(result.correctedTranscript()).isEqualTo(capture.getCorrectedTranscript());
        assertThat(result.correctionCount()).isEqualTo(1);
        verify(aiClient, never()).correctBrowserTranscript(any(), any());
    }

    private TranscriptCorrectionService.CorrectionResult correct() {
        return service.correct(sessionId, answerId, captureId, 1, context());
    }

    private TranscriptCorrectionContext context() {
        return new TranscriptCorrectionContext(
                "Bạn dùng Postman như thế nào?",
                answer.getRawTranscript(),
                List.of("Spring Boot", "Postman"),
                List.of("REST API"),
                List.of("bug"),
                "");
    }

    private ShopAiKeyClient.TranscriptCorrectionDraft draft(
            String correctedTranscript,
            List<ShopAiKeyClient.TranscriptCorrectionItemDraft> corrections
    ) {
        return new ShopAiKeyClient.TranscriptCorrectionDraft(
                correctedTranscript,
                corrections,
                List.of(new ShopAiKeyClient.TranscriptEvidenceDraft(
                        "technical_knowledge", "Sử dụng Spring Boot và Postman")),
                "Ứng viên mô tả công cụ đã sử dụng.",
                true,
                "Chưa nêu kết quả.");
    }

    private ShopAiKeyClient.TranscriptCorrectionItemDraft correction(
            String original,
            String replacement,
            double confidence,
            String reason
    ) {
        return new ShopAiKeyClient.TranscriptCorrectionItemDraft(original, replacement, confidence, reason);
    }
}
