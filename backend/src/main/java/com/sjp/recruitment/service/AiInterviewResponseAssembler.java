package com.sjp.recruitment.service;

import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.model.entity.*;
import com.sjp.recruitment.model.enums.InterviewTurnAnswerStatus;
import com.sjp.recruitment.repository.*;
import com.sjp.recruitment.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AiInterviewResponseAssembler {

    private static final int TOTAL_CORE_QUESTIONS = 5;

    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final AiAnswerFeedbackRepository answerFeedbackRepository;
    private final AiSessionFeedbackRepository sessionFeedbackRepository;
    private final InterviewConversationTurnRepository conversationTurnRepository;
    private final JobService jobService;

    @Transactional(readOnly = true)
    public AiInterviewSessionResponse assemble(InterviewSession session) {
        return assembleAll(List.of(session)).get(0);
    }

    @Transactional(readOnly = true)
    public List<AiInterviewSessionResponse> assembleAll(List<InterviewSession> sessions) {
        if (sessions.isEmpty()) return List.of();

        List<UUID> sessionIds = sessions.stream().map(InterviewSession::getId).toList();
        Map<UUID, List<InterviewQuestion>> questionsBySession = questionRepository
                .findBySessionIdInOrderBySessionIdAscOrderIndexAsc(sessionIds)
                .stream()
                .collect(Collectors.groupingBy(question -> question.getSession().getId()));
        List<InterviewAnswer> allAnswers = answerRepository
                .findBySessionIdInOrderBySessionIdAscAnsweredAtAsc(sessionIds);
        Map<UUID, Map<UUID, InterviewAnswer>> answersBySession = allAnswers.stream()
                .collect(Collectors.groupingBy(
                        answer -> answer.getSession().getId(),
                        Collectors.toMap(InterviewAnswer::getQuestionId, answer -> answer, (left, right) -> left)
                ));
        Map<UUID, AiAnswerFeedback> feedbackByAnswer = allAnswers.isEmpty()
                ? Map.of()
                : answerFeedbackRepository.findByAnswerIdIn(allAnswers.stream().map(InterviewAnswer::getId).toList())
                .stream()
                .collect(Collectors.toMap(feedback -> feedback.getAnswer().getId(), feedback -> feedback));
        Map<UUID, AiSessionFeedback> summaryBySession = sessionFeedbackRepository.findBySessionIdIn(sessionIds)
                .stream()
                .collect(Collectors.toMap(feedback -> feedback.getSession().getId(), feedback -> feedback));
        Map<UUID, List<InterviewConversationTurn>> turnsBySession = conversationTurnRepository
                .findBySessionIdInOrderBySessionIdAscSequenceNoAsc(sessionIds)
                .stream()
                .collect(Collectors.groupingBy(turn -> turn.getSession().getId()));

        return sessions.stream()
                .map(session -> buildResponse(
                        session,
                        questionsBySession.getOrDefault(session.getId(), List.of()),
                        answersBySession.getOrDefault(session.getId(), Map.of()),
                        feedbackByAnswer,
                        summaryBySession.get(session.getId()),
                        turnsBySession.getOrDefault(session.getId(), List.of())
                ))
                .toList();
    }

    private AiInterviewSessionResponse buildResponse(InterviewSession session,
                                                      List<InterviewQuestion> questions,
                                                      Map<UUID, InterviewAnswer> answers,
                                                      Map<UUID, AiAnswerFeedback> feedbackByAnswer,
                                                      AiSessionFeedback sessionFeedback,
                                                      List<InterviewConversationTurn> conversationTurns) {
        List<AiInterviewQuestionResponse> questionResponses = session.getDialogueState() == null
                ? questions.stream()
                .map(question -> toQuestionResponse(
                        question,
                        answers.get(question.getId()),
                        feedbackByAnswer,
                        session.isCompleted()
                ))
                .toList()
                : List.of();
        return new AiInterviewSessionResponse(
                session.getId().toString(),
                session.getTitle(),
                session.getContextType(),
                session.getStatus(),
                session.getTotalQuestions() == null ? 0 : session.getTotalQuestions(),
                session.isCompleted() ? session.getOverallScore() : null,
                session.getApplication() == null ? null : session.getApplication().getId().toString(),
                session.getJob() == null ? null : jobService.toJobResponse(session.getJob(), session.getCandidate()),
                session.getPracticeContext() == null ? Map.of() : session.getPracticeContext(),
                asString(session.getStartedAt()),
                asString(session.getCompletedAt()),
                asString(session.getCreatedAt()),
                asString(session.getUpdatedAt()),
                questionResponses,
                !session.isCompleted() || sessionFeedback == null ? null : toSummaryResponse(sessionFeedback),
                toConversationResponse(session, answers, conversationTurns)
        );
    }

    private AiInterviewConversationResponse toConversationResponse(InterviewSession session,
                                                                    Map<UUID, InterviewAnswer> answers,
                                                                    List<InterviewConversationTurn> turns) {
        if (session.getDialogueState() == null && turns.isEmpty()) return null;

        InterviewConversationTurn currentTurn = resolveCurrentTurn(session, turns);
        List<InterviewConversationTurn> publishedTurns = publishedTurns(currentTurn, turns);
        UUID currentTurnId = currentTurn == null ? null : currentTurn.getId();
        List<AiInterviewConversationTurnResponse> timeline = publishedTurns.stream()
                .map(turn -> toConversationTurnResponse(turn, Objects.equals(turn.getId(), currentTurnId)))
                .toList();
        long completedCoreQuestions = answers.values().stream()
                .filter(answer -> answer.getAnsweredAt() != null)
                .count();

        return new AiInterviewConversationResponse(
                session.getDialogueState() == null ? null : session.getDialogueState().name(),
                session.getDialogueVersion() == null ? 1 : session.getDialogueVersion(),
                currentTurnId == null ? null : currentTurnId.toString(),
                expectsAnswer(currentTurn),
                speechText(publishedTurns),
                Math.toIntExact(Math.min(completedCoreQuestions, TOTAL_CORE_QUESTIONS)),
                TOTAL_CORE_QUESTIONS,
                session.getLastErrorStage(),
                session.getLastErrorCode(),
                session.getLastErrorMessage(),
                timeline
        );
    }

    private InterviewConversationTurn resolveCurrentTurn(InterviewSession session,
                                                           List<InterviewConversationTurn> turns) {
        InterviewConversationTurn currentTurnReference = session.getCurrentTurn();
        if (currentTurnReference == null) return null;

        UUID currentTurnId = currentTurnReference.getId();
        if (currentTurnId != null) {
            return turns.stream()
                    .filter(turn -> currentTurnId.equals(turn.getId()))
                    .findFirst()
                    .orElseThrow(this::invalidConversationState);
        }
        throw invalidConversationState();
    }

    private ApiException invalidConversationState() {
        return new ApiException(HttpStatus.CONFLICT, "INTERVIEW_CONVERSATION_STATE_INVALID",
                "Lượt hội thoại hiện tại không còn thuộc timeline của phiên phỏng vấn");
    }

    private List<InterviewConversationTurn> publishedTurns(InterviewConversationTurn currentTurn,
                                                           List<InterviewConversationTurn> turns) {
        if (currentTurn == null) return List.copyOf(turns);
        return turns.stream()
                .filter(turn -> turn.getSequenceNo() <= currentTurn.getSequenceNo())
                .toList();
    }

    private AiInterviewConversationTurnResponse toConversationTurnResponse(InterviewConversationTurn turn,
                                                                            boolean current) {
        return new AiInterviewConversationTurnResponse(
                turn.getId().toString(),
                turn.getSequenceNo(),
                turn.getText(),
                turn.getCandidateRawAnswer(),
                turn.getCandidateFinalAnswer(),
                turn.isTranscriptEdited(),
                turn.getEditCount(),
                turn.getAnswerStatus().name(),
                current,
                asString(turn.getAnsweredAt()),
                asString(turn.getCreatedAt())
        );
    }

    private boolean expectsAnswer(InterviewConversationTurn currentTurn) {
        if (currentTurn == null || currentTurn.getAnswerStatus() == null) return false;
        return switch (currentTurn.getAnswerStatus()) {
            case WAITING, PROCESSING, REVIEWING -> true;
            case NOT_REQUIRED, CONFIRMED, SKIPPED -> false;
        };
    }

    private String speechText(List<InterviewConversationTurn> publishedTurns) {
        if (publishedTurns.isEmpty()) return null;

        LinkedList<String> consecutiveInterviewerText = new LinkedList<>();
        for (int index = publishedTurns.size() - 1; index >= 0; index--) {
            InterviewConversationTurn turn = publishedTurns.get(index);
            if (hasCandidateResponse(turn)) break;
            if (turn.getText() != null && !turn.getText().isBlank()) {
                consecutiveInterviewerText.addFirst(turn.getText().trim());
            }
        }
        return consecutiveInterviewerText.isEmpty()
                ? null
                : String.join("\n", consecutiveInterviewerText);
    }

    private boolean hasCandidateResponse(InterviewConversationTurn turn) {
        if (turn.getCandidateRawAnswer() != null && !turn.getCandidateRawAnswer().isBlank()) return true;
        if (turn.getCandidateFinalAnswer() != null && !turn.getCandidateFinalAnswer().isBlank()) return true;
        return turn.getAnswerStatus() == InterviewTurnAnswerStatus.CONFIRMED
                || turn.getAnswerStatus() == InterviewTurnAnswerStatus.SKIPPED;
    }

    private AiInterviewQuestionResponse toQuestionResponse(InterviewQuestion question,
                                                            InterviewAnswer answer,
                                                            Map<UUID, AiAnswerFeedback> feedbackByAnswer,
                                                            boolean sessionCompleted) {
        return new AiInterviewQuestionResponse(
                question.getId().toString(),
                question.getOrderIndex(),
                question.getQuestionType(),
                question.getContent(),
                question.getDifficulty(),
                question.getSkillTag(),
                question.getTimeLimitSeconds(),
                question.getReplayCount(),
                question.getSourceType(),
                question.getSourceId(),
                question.getPromptVersion(),
                question.getRubricVersion(),
                question.getCompetencyId(),
                answer == null ? null : toAnswerResponse(
                        answer,
                        sessionCompleted ? feedbackByAnswer.get(answer.getId()) : null
                )
        );
    }

    private AiInterviewAnswerResponse toAnswerResponse(InterviewAnswer answer, AiAnswerFeedback feedback) {
        return new AiInterviewAnswerResponse(
                answer.getId().toString(),
                answer.getQuestionId().toString(),
                answer.getTranscriptText(),
                answer.getRawTranscript(),
                answer.getFinalTranscript(),
                answer.isTranscriptEdited(),
                answer.getTranscriptEditCount(),
                answer.getConversationState(),
                answer.isSkipped(),
                answer.getTranscriptStatus(),
                answer.getFeedbackStatus(),
                answer.getErrorMessage(),
                asString(answer.getAnsweredAt()),
                feedback == null ? null : toFeedbackResponse(feedback)
        );
    }

    private AiInterviewFeedbackResponse toFeedbackResponse(AiAnswerFeedback feedback) {
        return new AiInterviewFeedbackResponse(
                feedback.getId().toString(),
                feedback.getOverallScore(),
                feedback.getEvaluationStatus(),
                feedback.getBarsLevel(),
                feedback.getScoreReason(),
                feedback.getFeedback(),
                splitLines(feedback.getStrengths()),
                splitLines(feedback.getWeaknesses()),
                splitLines(feedback.getSuggestions()),
                feedback.getAnswer().getEvaluationSource(),
                feedback.getAnswer().isEvaluationFallback()
        );
    }

    private AiInterviewSessionSummaryResponse toSummaryResponse(AiSessionFeedback feedback) {
        return new AiInterviewSessionSummaryResponse(
                feedback.getOverallScore(),
                feedback.getContentScore(),
                feedback.getVoiceDeliveryScore(),
                feedback.getRawVoiceDeliveryScore(),
                feedback.getVoiceWeight(),
                feedback.getReplayCount(),
                feedback.getReplayPenalty(),
                feedback.getVoiceEvidenceQuestionCount(),
                feedback.getManualFallbackQuestionCount(),
                feedback.isReferenceOnly(),
                feedback.getAiSummary(),
                splitLines(feedback.getStrengths()),
                splitLines(feedback.getWeaknesses()),
                splitLines(feedback.getSuggestions()),
                feedback.getEvaluationProfileVersion(),
                feedback.getRubricVersion(),
                feedback.getSpeechCalibrationVersion(),
                feedback.getEvaluationSource(),
                feedback.isEvaluationFallback()
        );
    }

    private List<String> splitLines(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("\\R")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private String asString(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
