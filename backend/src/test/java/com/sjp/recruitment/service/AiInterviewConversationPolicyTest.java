package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.enums.AnswerAnalysisAction;
import com.sjp.recruitment.model.enums.InterviewDialogueState;
import com.sjp.recruitment.model.enums.InterviewTurnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiInterviewConversationPolicyTest {

    private AiInterviewConversationPolicy policy;

    @BeforeEach
    void setUp() {
        AiInterviewProperties properties = new AiInterviewProperties();
        properties.setMaxProbesPerCore(1);
        properties.setMaxClarifiesPerCore(1);
        properties.setMaxFollowUpsPerCore(1);
        properties.setMaxTotalAssessmentTurns(10);
        policy = new AiInterviewConversationPolicy(properties);
    }

    @Test
    void acceptsNextProbeAndClarifyWithinLimits() {
        assertThat(policy.resolveAction("NEXT", 0, 0, 5)).isEqualTo(AnswerAnalysisAction.NEXT);
        assertThat(policy.resolveAction("PROBE", 0, 0, 5)).isEqualTo(AnswerAnalysisAction.PROBE);
        assertThat(policy.resolveAction("CLARIFY", 0, 0, 5)).isEqualTo(AnswerAnalysisAction.CLARIFY);
    }

    @Test
    void forcesNextWhenPerCoreOrTotalFollowUpLimitIsReached() {
        assertThat(policy.resolveAction("PROBE", 1, 0, 5)).isEqualTo(AnswerAnalysisAction.NEXT);
        assertThat(policy.resolveAction("CLARIFY", 0, 1, 5)).isEqualTo(AnswerAnalysisAction.NEXT);
        assertThat(policy.resolveAction("CLARIFY", 1, 0, 5)).isEqualTo(AnswerAnalysisAction.NEXT);
        assertThat(policy.resolveAction("PROBE", 0, 1, 5)).isEqualTo(AnswerAnalysisAction.NEXT);
        assertThat(policy.resolveAction("PROBE", 0, 0, 10)).isEqualTo(AnswerAnalysisAction.NEXT);
        assertThat(policy.resolveAction("CLARIFY", 0, 0, 10)).isEqualTo(AnswerAnalysisAction.NEXT);
    }

    @Test
    void reservesEnoughAssessmentTurnsForAllFiveCoreQuestions() {
        assertThat(policy.resolveAction("PROBE", 0, 0, 6, 4))
                .isEqualTo(AnswerAnalysisAction.NEXT);
        assertThat(policy.resolveAction("PROBE", 0, 0, 5, 4))
                .isEqualTo(AnswerAnalysisAction.PROBE);
    }

    @Test
    void rejectsUnknownProviderActionInsteadOfCreatingFallbackBehavior() {
        assertThatThrownBy(() -> policy.resolveAction("CHAT", 0, 0, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyCoreProbeAndClarifyAreAssessmentTurns() {
        assertThat(policy.isAssessmentTurn(InterviewTurnType.CORE_QUESTION)).isTrue();
        assertThat(policy.isAssessmentTurn(InterviewTurnType.PROBE)).isTrue();
        assertThat(policy.isAssessmentTurn(InterviewTurnType.CLARIFY)).isTrue();
        assertThat(policy.isAssessmentTurn(InterviewTurnType.OPENING)).isFalse();
        assertThat(policy.isAssessmentTurn(InterviewTurnType.ACKNOWLEDGEMENT)).isFalse();
        assertThat(policy.isAssessmentTurn(InterviewTurnType.TRANSITION)).isFalse();
        assertThat(policy.isAssessmentTurn(InterviewTurnType.CLOSING)).isFalse();
    }

    @Test
    void enforcesDialogueStateMachineTransitions() {
        assertThat(policy.canTransition(InterviewDialogueState.WAITING_ANSWER,
                InterviewDialogueState.ANALYZE_ANSWER)).isTrue();
        assertThat(policy.canTransition(InterviewDialogueState.ANALYZE_ANSWER,
                InterviewDialogueState.ASK_PROBE)).isTrue();
        assertThat(policy.canTransition(InterviewDialogueState.ANALYZE_ANSWER,
                InterviewDialogueState.ASK_CLARIFY)).isTrue();
        assertThat(policy.canTransition(InterviewDialogueState.ANALYZE_ANSWER,
                InterviewDialogueState.ACK_TRANSITION)).isTrue();
        assertThat(policy.canTransition(InterviewDialogueState.ACK_TRANSITION,
                InterviewDialogueState.REVIEW_TRANSCRIPTS)).isTrue();
        assertThat(policy.canTransition(InterviewDialogueState.REVIEW_TRANSCRIPTS,
                InterviewDialogueState.CLOSING)).isTrue();
        assertThat(policy.canTransition(InterviewDialogueState.WAITING_ANSWER,
                InterviewDialogueState.COMPLETED)).isFalse();
    }
}
