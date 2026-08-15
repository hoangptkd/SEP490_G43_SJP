package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.AiInterviewApplicationSessionRequest;
import com.sjp.recruitment.model.dto.request.AiInterviewConfirmAnswerRequest;
import com.sjp.recruitment.model.dto.request.AiInterviewConfirmTurnRequest;
import com.sjp.recruitment.model.dto.request.AiInterviewCvProfileRequest;
import com.sjp.recruitment.model.dto.request.AiInterviewFinishRequest;
import com.sjp.recruitment.model.dto.request.AiInterviewLiveTranscriptionRequest;
import com.sjp.recruitment.model.dto.request.AiInterviewPracticeSessionRequest;
import com.sjp.recruitment.model.dto.request.AiInterviewSpeechRequest;
import com.sjp.recruitment.model.dto.request.AiInterviewSubmitAnswerRequest;
import com.sjp.recruitment.model.dto.request.AiInterviewTurnCommandRequest;
import com.sjp.recruitment.model.dto.response.AiInterviewConfigResponse;
import com.sjp.recruitment.model.dto.response.AiInterviewCvProfileResponse;
import com.sjp.recruitment.model.dto.response.AiInterviewEligibleApplicationResponse;
import com.sjp.recruitment.model.dto.response.AiInterviewLiveTranscriptionResponse;
import com.sjp.recruitment.model.dto.response.AiInterviewQuestionSetResponse;
import com.sjp.recruitment.model.dto.response.AiInterviewSessionResponse;
import com.sjp.recruitment.model.dto.response.AiInterviewSpeechTicketResponse;
import com.sjp.recruitment.model.dto.response.AiInterviewTranscriptResponse;
import com.sjp.recruitment.model.dto.response.HandsFreeAnswerCaptureResponse;
import com.sjp.recruitment.service.AiInterviewService;
import com.sjp.recruitment.service.AiInterviewCvProfileService;
import com.sjp.recruitment.service.AiInterviewSpeechService;
import com.sjp.recruitment.service.HandsFreeAnswerCaptureService;
import com.sjp.recruitment.service.GladiaLiveSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.concurrent.Callable;

@RestController
@RequestMapping("/candidate/ai-interviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CANDIDATE')")
public class AiInterviewController {

    private final AiInterviewService aiInterviewService;
    private final AiInterviewCvProfileService aiInterviewCvProfileService;
    private final AiInterviewSpeechService aiInterviewSpeechService;
    private final HandsFreeAnswerCaptureService handsFreeAnswerCaptureService;
    private final GladiaLiveSessionService gladiaLiveSessionService;

    @GetMapping("/config-status")
    public ResponseEntity<AiInterviewConfigResponse> configStatus() {
        return ResponseEntity.ok(aiInterviewService.configStatus());
    }

    @GetMapping("/eligible-applications")
    public ResponseEntity<List<AiInterviewEligibleApplicationResponse>> eligibleApplications() {
        return ResponseEntity.ok(aiInterviewService.eligibleApplications());
    }

    @GetMapping("/question-sets")
    public ResponseEntity<List<AiInterviewQuestionSetResponse>> questionSets() {
        return ResponseEntity.ok(aiInterviewService.questionSets());
    }

    @PostMapping("/sessions/{sessionId}/live-transcription")
    public ResponseEntity<AiInterviewLiveTranscriptionResponse> createLiveTranscription(
            @PathVariable String sessionId,
            @Valid @RequestBody AiInterviewLiveTranscriptionRequest request) {
        return ResponseEntity.ok(gladiaLiveSessionService.create(sessionId, request.sampleRate()));
    }

    @PostMapping("/sessions/application")
    public ResponseEntity<AiInterviewSessionResponse> createApplicationSession(@Valid @RequestBody AiInterviewApplicationSessionRequest request) {
        return ResponseEntity.ok(aiInterviewService.createApplicationSession(request.applicationId()));
    }

    @PostMapping("/sessions/practice")
    public ResponseEntity<AiInterviewSessionResponse> createPracticeSession(@Valid @RequestBody AiInterviewPracticeSessionRequest request) {
        return ResponseEntity.ok(aiInterviewService.createPracticeSession(request));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<AiInterviewSessionResponse>> sessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(aiInterviewService.sessions(page, size));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<AiInterviewSessionResponse> session(@PathVariable String sessionId) {
        return ResponseEntity.ok(aiInterviewService.session(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        aiInterviewService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/sessions/{sessionId}/questions/current/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Callable<ResponseEntity<AiInterviewTranscriptResponse>> transcribeCurrentQuestion(
            @PathVariable String sessionId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "durationSeconds", required = false) Integer durationSeconds) {
        return () -> ResponseEntity.ok(aiInterviewService.transcribeCurrentQuestion(sessionId, file, durationSeconds));
    }

    @PostMapping("/practice/cv-profile")
    public ResponseEntity<AiInterviewCvProfileResponse> analyzePracticeCv(
            @Valid @RequestBody AiInterviewCvProfileRequest request) {
        return ResponseEntity.ok(aiInterviewCvProfileService.analyze(request.cvId()));
    }

    @PostMapping(value = "/sessions/{sessionId}/questions/{questionId}/answer-capture",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Callable<ResponseEntity<HandsFreeAnswerCaptureResponse>> processAnswerCapture(
            @PathVariable String sessionId,
            @PathVariable String questionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam("captureId") String captureId,
            @RequestParam(value = "captureVersion", required = false) Integer captureVersion,
            @RequestPart("audioSegments") List<MultipartFile> audioSegments,
            @RequestParam("segmentSequences") List<Integer> segmentSequences,
            @RequestParam(value = "browserTranscript", required = false) String browserTranscript,
            @RequestParam(value = "transcriptionSource", required = false) String transcriptionSource,
            @RequestParam(value = "liveSessionToken", required = false) String liveSessionToken,
            @RequestParam(value = "durationSeconds", required = false) List<Double> durationSeconds) {
        return () -> ResponseEntity.ok(handsFreeAnswerCaptureService.process(
                sessionId, questionId, idempotencyKey, captureId, captureVersion == null ? 1 : captureVersion,
                audioSegments, segmentSequences, browserTranscript, transcriptionSource, liveSessionToken,
                durationSeconds));
    }

    @PostMapping(value = "/sessions/{sessionId}/turns/{turnId}/answer-capture",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Callable<ResponseEntity<HandsFreeAnswerCaptureResponse>> processTurnAnswerCapture(
            @PathVariable String sessionId,
            @PathVariable String turnId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam("captureId") String captureId,
            @RequestParam(value = "captureVersion", required = false) Integer captureVersion,
            @RequestPart("audioSegments") List<MultipartFile> audioSegments,
            @RequestParam("segmentSequences") List<Integer> segmentSequences,
            @RequestParam(value = "browserTranscript", required = false) String browserTranscript,
            @RequestParam(value = "transcriptionSource", required = false) String transcriptionSource,
            @RequestParam(value = "liveSessionToken", required = false) String liveSessionToken,
            @RequestParam(value = "durationSeconds", required = false) List<Double> durationSeconds) {
        return () -> ResponseEntity.ok(handsFreeAnswerCaptureService.processTurn(
                sessionId, turnId, idempotencyKey, captureId, captureVersion == null ? 1 : captureVersion,
                audioSegments, segmentSequences, browserTranscript, transcriptionSource, liveSessionToken,
                durationSeconds));
    }

    @PostMapping("/sessions/{sessionId}/speech")
    public ResponseEntity<AiInterviewSpeechTicketResponse> createSpeechTicket(
            @PathVariable String sessionId,
            @Valid @RequestBody AiInterviewSpeechRequest request) {
        aiInterviewService.session(sessionId);
        return ResponseEntity.ok(aiInterviewSpeechService.createTicket(request.input()));
    }

    @PostMapping("/sessions/{sessionId}/questions/{questionId}/answer")
    public ResponseEntity<AiInterviewSessionResponse> submitAnswer(
            @PathVariable String sessionId,
            @PathVariable String questionId,
            @Valid @RequestBody AiInterviewSubmitAnswerRequest request) {
        return ResponseEntity.ok(aiInterviewService.submitAnswer(sessionId, questionId, request.transcript()));
    }

    @PostMapping("/sessions/{sessionId}/questions/{questionId}/replay")
    public ResponseEntity<AiInterviewSessionResponse> replayQuestion(
            @PathVariable String sessionId,
            @PathVariable String questionId) {
        return ResponseEntity.ok(aiInterviewService.replayQuestion(sessionId, questionId));
    }

    @PostMapping("/sessions/{sessionId}/questions/{questionId}/confirm")
    public ResponseEntity<AiInterviewSessionResponse> confirmAnswer(
            @PathVariable String sessionId,
            @PathVariable String questionId,
            @Valid @RequestBody AiInterviewConfirmAnswerRequest request) {
        return ResponseEntity.ok(aiInterviewService.confirmAnswer(
                sessionId, questionId, request.rawTranscript(), request.finalTranscript()));
    }

    @PostMapping("/sessions/{sessionId}/finish")
    public ResponseEntity<AiInterviewSessionResponse> finishInterview(
            @PathVariable String sessionId,
            @Valid @RequestBody AiInterviewFinishRequest request) {
        return ResponseEntity.ok(aiInterviewService.finishInterview(
                sessionId,
                request.questionId(),
                request.transcript()
        ));
    }

    @PostMapping("/sessions/{sessionId}/questions/{questionId}/skip")
    public ResponseEntity<AiInterviewSessionResponse> skipQuestion(
            @PathVariable String sessionId,
            @PathVariable String questionId) {
        return ResponseEntity.ok(aiInterviewService.skipQuestion(sessionId, questionId));
    }

    @PostMapping("/sessions/{sessionId}/turns/{turnId}/confirm")
    public ResponseEntity<AiInterviewSessionResponse> confirmConversationTurn(
            @PathVariable String sessionId,
            @PathVariable String turnId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AiInterviewConfirmTurnRequest request) {
        return ResponseEntity.ok(aiInterviewService.confirmConversationTurn(
                sessionId,
                turnId,
                idempotencyKey,
                request.rawTranscript(),
                request.finalTranscript(),
                request.expectedDialogueVersion()
        ));
    }

    @PostMapping("/sessions/{sessionId}/turns/{turnId}/skip")
    public ResponseEntity<AiInterviewSessionResponse> skipConversationTurn(
            @PathVariable String sessionId,
            @PathVariable String turnId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AiInterviewTurnCommandRequest request) {
        return ResponseEntity.ok(aiInterviewService.skipConversationTurn(
                sessionId,
                turnId,
                idempotencyKey,
                request.expectedDialogueVersion()
        ));
    }

    @PostMapping("/sessions/{sessionId}/turns/{turnId}/replay")
    public ResponseEntity<AiInterviewSessionResponse> replayConversationTurn(
            @PathVariable String sessionId,
            @PathVariable String turnId,
            @Valid @RequestBody AiInterviewTurnCommandRequest request) {
        return ResponseEntity.ok(aiInterviewService.replayConversationTurn(
                sessionId,
                turnId,
                request.expectedDialogueVersion()
        ));
    }

    @PostMapping("/sessions/{sessionId}/conversation/retry")
    public ResponseEntity<AiInterviewSessionResponse> retryConversation(
            @PathVariable String sessionId) {
        return ResponseEntity.ok(aiInterviewService.retryConversation(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/questions/retry")
    public ResponseEntity<AiInterviewSessionResponse> retryQuestionGeneration(@PathVariable String sessionId) {
        return ResponseEntity.ok(aiInterviewService.retryQuestionGeneration(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/questions/{questionId}/feedback/retry")
    public ResponseEntity<AiInterviewSessionResponse> retryFeedback(
            @PathVariable String sessionId,
            @PathVariable String questionId) {
        return ResponseEntity.ok(aiInterviewService.retryFeedback(sessionId, questionId));
    }

    @PostMapping("/sessions/{sessionId}/summary/retry")
    public ResponseEntity<AiInterviewSessionResponse> retrySummary(@PathVariable String sessionId) {
        return ResponseEntity.ok(aiInterviewService.retrySummary(sessionId));
    }
}
