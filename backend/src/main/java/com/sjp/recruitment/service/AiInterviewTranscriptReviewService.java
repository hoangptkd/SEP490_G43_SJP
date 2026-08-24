package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewAnswerCapture;
import com.sjp.recruitment.model.entity.InterviewConversationTurn;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.enums.InterviewDialogueState;
import com.sjp.recruitment.model.enums.InterviewTurnAnswerStatus;
import com.sjp.recruitment.model.enums.InterviewTurnType;
import com.sjp.recruitment.model.enums.TranscriptCorrectionStatus;
import com.sjp.recruitment.model.enums.TranscriptReviewAction;
import com.sjp.recruitment.repository.InterviewAnswerCaptureRepository;
import com.sjp.recruitment.repository.InterviewAnswerRepository;
import com.sjp.recruitment.repository.InterviewConversationTurnRepository;
import com.sjp.recruitment.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiInterviewTranscriptReviewService {

    private static final String DECISION_PENDING = "PENDING";
    private static final String DECISION_ACCEPTED = "ACCEPTED";
    private static final String DECISION_REJECTED = "REJECTED";
    private static final String DECISION_MANUAL_EDIT = "MANUAL_EDIT";
    private static final String DECISION_AUTO_KEPT = "AUTO_KEPT";

    private final CandidateService candidateService;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewConversationTurnRepository turnRepository;
    private final InterviewAnswerRepository answerRepository;
    private final InterviewAnswerCaptureRepository captureRepository;
    private final AiInterviewConversationPolicy conversationPolicy;
    private final TransactionTemplate transactions;

    public void review(
            UUID sessionId,
            UUID turnId,
            TranscriptReviewAction action,
            UUID captureId,
            Integer captureVersion,
            String transcript,
            int expectedEditCount
    ) {
        inTransaction(() -> {
            InterviewSession session = lockOwnedSession(sessionId);
            requireReviewOpen(session);
            InterviewConversationTurn turn = turnRepository
                    .findByIdAndSessionIdForUpdate(turnId, sessionId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "INTERVIEW_TURN_NOT_FOUND",
                            "Không tìm thấy lượt hội thoại."));
            requireEditableTurn(turn);
            if (turn.getEditCount() != expectedEditCount) {
                throw conflict("TRANSCRIPT_EDIT_CONFLICT",
                        "Transcript đã được cập nhật ở nơi khác. Hãy tải lại nội dung mới nhất.");
            }

            InterviewAnswer answer = answerRepository
                    .findBySessionIdAndQuestionIdForUpdate(
                            sessionId, turn.getAssessmentItem().getId())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "ANSWER_NOT_FOUND",
                            "Không tìm thấy câu trả lời cần chỉnh sửa."));
            InterviewAnswerCapture capture = requireCapture(
                    action, answer, turn, captureId, captureVersion);

            String decidedTranscript = switch (action) {
                case ACCEPT_AI -> requireCorrectedTranscript(capture);
                case KEEP_CURRENT -> normalize(turn.getCandidateFinalAnswer());
                case MANUAL_EDIT -> requireManualTranscript(transcript);
            };
            if (action != TranscriptReviewAction.KEEP_CURRENT) {
                updateTurnTranscript(turn, decidedTranscript);
                turnRepository.save(turn);
                rebuildAggregateAnswer(sessionId, turn, answer);
            }
            if (capture != null) {
                persistCandidateDecision(capture, action, decidedTranscript);
            }
            return null;
        });
    }

    public void completeReview(UUID sessionId) {
        inTransaction(() -> {
            InterviewSession session = lockOwnedSession(sessionId);
            if (session.getDialogueState() != InterviewDialogueState.REVIEW_TRANSCRIPTS) {
                throw conflict("TRANSCRIPT_REVIEW_NOT_AVAILABLE",
                        "Phiên phỏng vấn chưa sẵn sàng để hoàn tất kiểm tra transcript.");
            }
            List<UUID> answerIds = answerRepository
                    .findBySessionIdOrderByAnsweredAtAsc(sessionId)
                    .stream()
                    .map(InterviewAnswer::getId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!answerIds.isEmpty()) {
                captureRepository.findByAnswerIdInForUpdate(answerIds)
                        .forEach(this::autoKeepUnresolved);
            }
            conversationPolicy.requireTransition(
                    InterviewDialogueState.REVIEW_TRANSCRIPTS,
                    InterviewDialogueState.CLOSING);
            session.setDialogueState(InterviewDialogueState.CLOSING);
            session.setDialogueVersion(value(session.getDialogueVersion(), 1) + 1);
            session.setLastErrorStage(null);
            session.setLastErrorCode(null);
            session.setLastErrorMessage(null);
            sessionRepository.save(session);
            return null;
        });
    }

    private InterviewSession lockOwnedSession(UUID sessionId) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        return sessionRepository.findOwnedForUpdate(sessionId, candidate.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SESSION_NOT_FOUND",
                        "Không tìm thấy phiên phỏng vấn."));
    }

    private void requireReviewOpen(InterviewSession session) {
        if (session.isCompleted()
                || session.getDialogueState() == InterviewDialogueState.CLOSING
                || session.getDialogueState() == InterviewDialogueState.COMPLETED) {
            throw conflict("TRANSCRIPT_REVIEW_LOCKED",
                    "Transcript đã bị khóa vì phiên đang được chấm hoặc đã hoàn thành.");
        }
    }

    private void requireEditableTurn(InterviewConversationTurn turn) {
        if (turn.getAssessmentItem() == null
                || turn.getAnswerStatus() != InterviewTurnAnswerStatus.CONFIRMED
                || (turn.getTurnType() != InterviewTurnType.CORE_QUESTION
                && turn.getTurnType() != InterviewTurnType.PROBE
                && turn.getTurnType() != InterviewTurnType.CLARIFY)) {
            throw conflict("TRANSCRIPT_TURN_NOT_EDITABLE",
                    "Lượt hội thoại này không có transcript có thể chỉnh sửa.");
        }
    }

    private InterviewAnswerCapture requireCapture(
            TranscriptReviewAction action,
            InterviewAnswer answer,
            InterviewConversationTurn turn,
            UUID captureId,
            Integer captureVersion
    ) {
        if (captureId == null || captureVersion == null) {
            if (action == TranscriptReviewAction.MANUAL_EDIT) return null;
            throw conflict("TRANSCRIPT_CAPTURE_REQUIRED",
                    "Thiếu định danh bản ghi transcript correction.");
        }
        InterviewAnswerCapture capture = captureRepository
                .findForUpdate(answer.getId(), captureId, captureVersion)
                .orElseThrow(() -> conflict(
                        "TRANSCRIPT_CAPTURE_STALE",
                        "Đề xuất sửa transcript đã cũ hoặc không còn tồn tại."));
        if (capture.getConversationTurn() == null
                || !turn.getId().equals(capture.getConversationTurn().getId())) {
            throw conflict("TRANSCRIPT_CAPTURE_MISMATCH",
                    "Đề xuất sửa transcript không thuộc lượt trả lời này.");
        }
        return capture;
    }

    private String requireCorrectedTranscript(InterviewAnswerCapture capture) {
        if (capture == null
                || capture.getTranscriptCorrectionStatus() != TranscriptCorrectionStatus.CORRECTED
                || !hasText(capture.getCorrectedTranscript())) {
            throw conflict("TRANSCRIPT_CORRECTION_NOT_READY",
                    "AI chưa có bản sửa transcript hợp lệ để áp dụng.");
        }
        return normalize(capture.getCorrectedTranscript());
    }

    private String requireManualTranscript(String transcript) {
        String normalized = normalize(transcript);
        if (normalized.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TRANSCRIPT_REQUIRED",
                    "Transcript chỉnh sửa không được để trống.");
        }
        return normalized;
    }

    private void updateTurnTranscript(
            InterviewConversationTurn turn,
            String decidedTranscript
    ) {
        if (normalizeForAudit(turn.getCandidateFinalAnswer())
                .equals(normalizeForAudit(decidedTranscript))) {
            return;
        }
        turn.setCandidateFinalAnswer(decidedTranscript);
        turn.setTranscriptEdited(!normalizeForAudit(turn.getCandidateRawAnswer())
                .equals(normalizeForAudit(decidedTranscript)));
        turn.setEditCount(turn.getEditCount() + 1);
    }

    private void rebuildAggregateAnswer(
            UUID sessionId,
            InterviewConversationTurn changedTurn,
            InterviewAnswer answer
    ) {
        List<InterviewConversationTurn> confirmed = turnRepository
                .findBySessionIdAndAssessmentItemIdOrderBySequenceNoAsc(
                        sessionId, changedTurn.getAssessmentItem().getId())
                .stream()
                .filter(turn -> turn.getAnswerStatus() == InterviewTurnAnswerStatus.CONFIRMED)
                .filter(turn -> turn.getTurnType() == InterviewTurnType.CORE_QUESTION
                        || turn.getTurnType() == InterviewTurnType.PROBE
                        || turn.getTurnType() == InterviewTurnType.CLARIFY)
                .filter(turn -> hasText(turn.getCandidateFinalAnswer()))
                .toList();
        String finalTranscript = confirmed.stream()
                .map(InterviewConversationTurn::getCandidateFinalAnswer)
                .map(this::normalize)
                .collect(Collectors.joining("\n"));
        answer.setFinalTranscript(finalTranscript);
        answer.setTranscriptText(finalTranscript);
        answer.setTranscriptEdited(confirmed.stream()
                .anyMatch(InterviewConversationTurn::isTranscriptEdited));
        answer.setTranscriptEditCount(confirmed.stream()
                .mapToInt(InterviewConversationTurn::getEditCount)
                .sum());
        answerRepository.save(answer);
    }

    private void persistCandidateDecision(
            InterviewAnswerCapture capture,
            TranscriptReviewAction action,
            String decidedTranscript
    ) {
        Map<String, Object> metadata = capture.getTranscriptCorrectionJson() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(capture.getTranscriptCorrectionJson());
        metadata.put("candidateDecision", switch (action) {
            case ACCEPT_AI -> DECISION_ACCEPTED;
            case KEEP_CURRENT -> DECISION_REJECTED;
            case MANUAL_EDIT -> DECISION_MANUAL_EDIT;
        });
        metadata.put("decidedAt", LocalDateTime.now().toString());
        metadata.put("decidedTranscript", decidedTranscript);
        capture.setTranscriptCorrectionJson(metadata);
        captureRepository.save(capture);
    }

    private void autoKeepUnresolved(InterviewAnswerCapture capture) {
        Map<String, Object> metadata = capture.getTranscriptCorrectionJson() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(capture.getTranscriptCorrectionJson());
        Object storedDecision = metadata.get("candidateDecision");
        String decision = storedDecision == null
                ? DECISION_PENDING
                : String.valueOf(storedDecision);
        if (!DECISION_PENDING.equals(decision)) return;
        metadata.put("candidateDecision", DECISION_AUTO_KEPT);
        metadata.put("decidedAt", LocalDateTime.now().toString());
        metadata.put("decidedTranscript", normalize(capture.getConversationTurn() == null
                ? capture.getRawTranscript()
                : capture.getConversationTurn().getCandidateFinalAnswer()));
        capture.setTranscriptCorrectionJson(metadata);
        captureRepository.save(capture);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeForAudit(String value) {
        return normalize(value).toLowerCase(java.util.Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
