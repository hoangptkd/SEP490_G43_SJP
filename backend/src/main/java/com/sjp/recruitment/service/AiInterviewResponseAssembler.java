package com.sjp.recruitment.service;

import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.model.entity.*;
import com.sjp.recruitment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AiInterviewResponseAssembler {

    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final AiAnswerFeedbackRepository answerFeedbackRepository;
    private final AiSessionFeedbackRepository sessionFeedbackRepository;
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

        return sessions.stream()
                .map(session -> buildResponse(
                        session,
                        questionsBySession.getOrDefault(session.getId(), List.of()),
                        answersBySession.getOrDefault(session.getId(), Map.of()),
                        feedbackByAnswer,
                        summaryBySession.get(session.getId())
                ))
                .toList();
    }

    private AiInterviewSessionResponse buildResponse(InterviewSession session,
                                                      List<InterviewQuestion> questions,
                                                      Map<UUID, InterviewAnswer> answers,
                                                      Map<UUID, AiAnswerFeedback> feedbackByAnswer,
                                                      AiSessionFeedback sessionFeedback) {
        List<AiInterviewQuestionResponse> questionResponses = questions.stream()
                .map(question -> toQuestionResponse(question, answers.get(question.getId()), feedbackByAnswer))
                .toList();
        return new AiInterviewSessionResponse(
                session.getId().toString(),
                session.getTitle(),
                session.getContextType(),
                session.getStatus(),
                session.getTotalQuestions() == null ? 0 : session.getTotalQuestions(),
                session.getOverallScore(),
                session.getApplication() == null ? null : session.getApplication().getId().toString(),
                session.getJob() == null ? null : jobService.toJobResponse(session.getJob(), session.getCandidate()),
                session.getPracticeContext() == null ? Map.of() : session.getPracticeContext(),
                asString(session.getStartedAt()),
                asString(session.getCompletedAt()),
                asString(session.getCreatedAt()),
                asString(session.getUpdatedAt()),
                questionResponses,
                sessionFeedback == null ? null : toSummaryResponse(sessionFeedback)
        );
    }

    private AiInterviewQuestionResponse toQuestionResponse(InterviewQuestion question,
                                                            InterviewAnswer answer,
                                                            Map<UUID, AiAnswerFeedback> feedbackByAnswer) {
        return new AiInterviewQuestionResponse(
                question.getId().toString(),
                question.getOrderIndex(),
                question.getQuestionType(),
                question.getContent(),
                question.getDifficulty(),
                question.getSkillTag(),
                question.getTimeLimitSeconds(),
                answer == null ? null : toAnswerResponse(answer, feedbackByAnswer.get(answer.getId()))
        );
    }

    private AiInterviewAnswerResponse toAnswerResponse(InterviewAnswer answer, AiAnswerFeedback feedback) {
        return new AiInterviewAnswerResponse(
                answer.getId().toString(),
                answer.getQuestionId().toString(),
                answer.getTranscriptText(),
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
                feedback.getAiSummary(),
                splitLines(feedback.getStrengths()),
                splitLines(feedback.getWeaknesses()),
                splitLines(feedback.getSuggestions()),
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
