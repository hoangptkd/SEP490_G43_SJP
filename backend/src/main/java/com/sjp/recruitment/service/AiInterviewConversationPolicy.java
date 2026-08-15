package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.enums.AnswerAnalysisAction;
import com.sjp.recruitment.model.enums.InterviewDialogueState;
import com.sjp.recruitment.model.enums.InterviewTurnType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AiInterviewConversationPolicy {

    private static final Map<InterviewDialogueState, Set<InterviewDialogueState>> TRANSITIONS = transitions();

    private final AiInterviewProperties properties;

    public AnswerAnalysisAction resolveAction(
            String requestedAction,
            int probeCount,
            int clarifyCount,
            int totalAssessmentTurns
    ) {
        return resolveAction(requestedAction, probeCount, clarifyCount, totalAssessmentTurns, 0);
    }

    public AnswerAnalysisAction resolveAction(
            String requestedAction,
            int probeCount,
            int clarifyCount,
            int totalAssessmentTurns,
            int remainingCoreQuestions
    ) {
        AnswerAnalysisAction requested = parseAction(requestedAction);
        if (requested == AnswerAnalysisAction.PROBE
                && probeCount >= properties.getMaxProbesPerCore()) {
            return AnswerAnalysisAction.NEXT;
        }
        if (requested == AnswerAnalysisAction.CLARIFY
                && clarifyCount >= properties.getMaxClarifiesPerCore()) {
            return AnswerAnalysisAction.NEXT;
        }
        if (requested != AnswerAnalysisAction.NEXT
                && totalAssessmentTurns + remainingCoreQuestions
                >= properties.getMaxTotalAssessmentTurns()) {
            return AnswerAnalysisAction.NEXT;
        }
        return requested;
    }

    public AnswerAnalysisAction parseAction(String value) {
        try {
            return AnswerAnalysisAction.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("AI answer analysis action must be NEXT, PROBE or CLARIFY", exception);
        }
    }

    public boolean isAssessmentTurn(InterviewTurnType type) {
        return type == InterviewTurnType.CORE_QUESTION
                || type == InterviewTurnType.PROBE
                || type == InterviewTurnType.CLARIFY;
    }

    public boolean canTransition(InterviewDialogueState from, InterviewDialogueState to) {
        return from == to || TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public void requireTransition(InterviewDialogueState from, InterviewDialogueState to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Invalid AI interview dialogue transition: " + from + " -> " + to);
        }
    }

    private static Map<InterviewDialogueState, Set<InterviewDialogueState>> transitions() {
        Map<InterviewDialogueState, Set<InterviewDialogueState>> values =
                new EnumMap<>(InterviewDialogueState.class);
        values.put(InterviewDialogueState.SESSION_START, EnumSet.of(InterviewDialogueState.OPENING));
        values.put(InterviewDialogueState.OPENING, EnumSet.of(InterviewDialogueState.ASK_CORE));
        values.put(InterviewDialogueState.ASK_CORE, EnumSet.of(InterviewDialogueState.WAITING_ANSWER));
        values.put(InterviewDialogueState.WAITING_ANSWER, EnumSet.of(InterviewDialogueState.ANALYZE_ANSWER));
        values.put(InterviewDialogueState.ANALYZE_ANSWER, EnumSet.of(
                InterviewDialogueState.ASK_PROBE,
                InterviewDialogueState.ASK_CLARIFY,
                InterviewDialogueState.ACK_TRANSITION));
        values.put(InterviewDialogueState.ASK_PROBE, EnumSet.of(InterviewDialogueState.WAITING_ANSWER));
        values.put(InterviewDialogueState.ASK_CLARIFY, EnumSet.of(InterviewDialogueState.WAITING_ANSWER));
        values.put(InterviewDialogueState.ACK_TRANSITION, EnumSet.of(
                InterviewDialogueState.ASK_CORE,
                InterviewDialogueState.CLOSING));
        values.put(InterviewDialogueState.CLOSING, EnumSet.of(InterviewDialogueState.COMPLETED));
        values.put(InterviewDialogueState.COMPLETED, EnumSet.noneOf(InterviewDialogueState.class));
        return Map.copyOf(values);
    }
}
