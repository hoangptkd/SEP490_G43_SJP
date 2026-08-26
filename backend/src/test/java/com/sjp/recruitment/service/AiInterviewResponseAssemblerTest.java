package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.model.dto.response.AiInterviewSessionResponse;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewAnswerCapture;
import com.sjp.recruitment.model.entity.InterviewConversationTurn;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.enums.InterviewDialogueState;
import com.sjp.recruitment.model.enums.InterviewTurnAnswerStatus;
import com.sjp.recruitment.model.enums.InterviewTurnType;
import com.sjp.recruitment.model.enums.TranscriptCorrectionStatus;
import com.sjp.recruitment.repository.AiAnswerFeedbackRepository;
import com.sjp.recruitment.repository.AiSessionFeedbackRepository;
import com.sjp.recruitment.repository.InterviewAnswerRepository;
import com.sjp.recruitment.repository.InterviewAnswerCaptureRepository;
import com.sjp.recruitment.repository.InterviewConversationTurnRepository;
import com.sjp.recruitment.repository.InterviewQuestionRepository;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiInterviewResponseAssemblerTest {

    @Test
    void resumeResponseKeepsRawFinalTranscriptAndReviewState() {
        InterviewQuestionRepository questionRepository = mock(InterviewQuestionRepository.class);
        InterviewAnswerRepository answerRepository = mock(InterviewAnswerRepository.class);
        AiAnswerFeedbackRepository answerFeedbackRepository = mock(AiAnswerFeedbackRepository.class);
        AiSessionFeedbackRepository sessionFeedbackRepository = mock(AiSessionFeedbackRepository.class);
        InterviewConversationTurnRepository conversationTurnRepository = mock(InterviewConversationTurnRepository.class);
        InterviewAnswerCaptureRepository answerCaptureRepository = mock(InterviewAnswerCaptureRepository.class);
        JobService jobService = mock(JobService.class);
        AiInterviewResponseAssembler assembler = new AiInterviewResponseAssembler(
                questionRepository,
                answerRepository,
                answerFeedbackRepository,
                sessionFeedbackRepository,
                conversationTurnRepository,
                answerCaptureRepository,
                jobService);

        InterviewSession session = new InterviewSession();
        session.setId(UUID.randomUUID());
        session.setTitle("Practice: Backend Developer");
        session.setContextType("practice");
        session.setStatus("in_progress");
        session.setTotalQuestions(5);
        session.setTargetQuestionCount(7);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(UUID.randomUUID());
        question.setSession(session);
        question.setOrderIndex(1);
        question.setQuestionType("behavioral");
        question.setContent("Hãy mô tả dự án của bạn.");
        question.setSourceType("AI_GENERATED");

        InterviewAnswer answer = new InterviewAnswer();
        answer.setId(UUID.randomUUID());
        answer.setSession(session);
        answer.setQuestionId(question.getId());
        answer.setRawTranscript("Em dùng spring bút.");
        answer.setFinalTranscript(null);
        answer.setTranscriptText(answer.getRawTranscript());
        answer.setConversationState("REVIEWING_TRANSCRIPT");
        answer.setTranscriptStatus("completed");
        answer.setFeedbackStatus("pending");

        when(questionRepository.findBySessionIdInOrderBySessionIdAscOrderIndexAsc(List.of(session.getId())))
                .thenReturn(List.of(question));
        when(answerRepository.findBySessionIdInOrderBySessionIdAscAnsweredAtAsc(List.of(session.getId())))
                .thenReturn(List.of(answer));
        when(answerFeedbackRepository.findByAnswerIdIn(List.of(answer.getId()))).thenReturn(List.of());
        when(sessionFeedbackRepository.findBySessionIdIn(List.of(session.getId()))).thenReturn(List.of());
        when(conversationTurnRepository.findBySessionIdInOrderBySessionIdAscSequenceNoAsc(List.of(session.getId())))
                .thenReturn(List.of());
        when(answerCaptureRepository.findByAnswerIdIn(List.of(answer.getId()))).thenReturn(List.of());

        AiInterviewSessionResponse response = assembler.assemble(session);

        assertThat(response.targetQuestionCount()).isEqualTo(7);
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).answer().rawTranscript()).isEqualTo("Em dùng spring bút.");
        assertThat(response.questions().get(0).answer().finalTranscript()).isNull();
        assertThat(response.questions().get(0).answer().conversationState()).isEqualTo("REVIEWING_TRANSCRIPT");
        assertThat(response.conversation()).isNull();
    }

    @Test
    void dialogueResponseOnlyPublishesSafeTimelineAndCombinesLatestInterviewerSpeech() throws Exception {
        InterviewQuestionRepository questionRepository = mock(InterviewQuestionRepository.class);
        InterviewAnswerRepository answerRepository = mock(InterviewAnswerRepository.class);
        AiAnswerFeedbackRepository answerFeedbackRepository = mock(AiAnswerFeedbackRepository.class);
        AiSessionFeedbackRepository sessionFeedbackRepository = mock(AiSessionFeedbackRepository.class);
        InterviewConversationTurnRepository conversationTurnRepository = mock(InterviewConversationTurnRepository.class);
        InterviewAnswerCaptureRepository answerCaptureRepository = mock(InterviewAnswerCaptureRepository.class);
        JobService jobService = mock(JobService.class);
        AiInterviewResponseAssembler assembler = new AiInterviewResponseAssembler(
                questionRepository,
                answerRepository,
                answerFeedbackRepository,
                sessionFeedbackRepository,
                conversationTurnRepository,
                answerCaptureRepository,
                jobService);

        InterviewSession session = new InterviewSession();
        session.setId(UUID.randomUUID());
        session.setTitle("Practice: Java Backend Developer");
        session.setContextType("practice");
        session.setStatus("in_progress");
        session.setTotalQuestions(3);
        session.setDialogueState(InterviewDialogueState.WAITING_ANSWER);
        session.setDialogueVersion(7);
        session.setLastErrorStage("ADAPTIVE_BATCH");
        session.setLastErrorCode("AI_PROVIDER_FAILED");
        session.setLastErrorMessage("Có thể thử lại mà không cần trả lời lại.");

        InterviewQuestion completedQuestion = question(session, 1, "Câu lõi đã hoàn tất");
        InterviewQuestion currentQuestion = question(session, 2, "Câu lõi hiện tại");
        InterviewQuestion futureQuestion = question(session, 3, "CÂU HỎI TƯƠNG LAI KHÔNG ĐƯỢC LỘ");
        futureQuestion.setCompetencyId("secret-competency");
        futureQuestion.setRubricVersion("secret-bars");

        InterviewAnswer completedAnswer = new InterviewAnswer();
        completedAnswer.setId(UUID.randomUUID());
        completedAnswer.setSession(session);
        completedAnswer.setQuestionId(completedQuestion.getId());
        completedAnswer.setAnsweredAt(LocalDateTime.of(2026, 8, 14, 10, 0));
        completedAnswer.setTranscriptStatus("completed");
        completedAnswer.setFeedbackStatus("pending");

        InterviewConversationTurn completedTurn = turn(
                session,
                completedQuestion,
                1,
                InterviewTurnType.CORE_QUESTION,
                InterviewTurnAnswerStatus.CONFIRMED,
                "Hãy mô tả dự án của bạn.");
        completedTurn.setCandidateRawAnswer("Em đã xây dựng API.");
        completedTurn.setCandidateFinalAnswer("Em đã xây dựng REST API.");
        completedTurn.setTranscriptEdited(true);
        completedTurn.setEditCount(1);
        completedTurn.setAnsweredAt(LocalDateTime.of(2026, 8, 14, 10, 0));

        InterviewAnswerCapture correction = new InterviewAnswerCapture();
        correction.setId(UUID.randomUUID());
        correction.setAnswer(completedAnswer);
        correction.setConversationTurn(completedTurn);
        correction.setCaptureId(UUID.randomUUID());
        correction.setCaptureVersion(1);
        correction.setTranscriptCorrectionStatus(TranscriptCorrectionStatus.CORRECTED);
        correction.setCorrectedTranscript("Em đã xây dựng RESTful API.");
        correction.setTranscriptCorrectionJson(Map.of(
                "corrections", List.of(Map.of(
                        "original", "REST API",
                        "replacement", "RESTful API",
                        "confidence", 0.97,
                        "reason", "technical_vocabulary"))));

        InterviewConversationTurn acknowledgement = turn(
                session,
                completedQuestion,
                2,
                InterviewTurnType.ACKNOWLEDGEMENT,
                InterviewTurnAnswerStatus.NOT_REQUIRED,
                "Được rồi.");
        InterviewConversationTurn transition = turn(
                session,
                currentQuestion,
                3,
                InterviewTurnType.TRANSITION,
                InterviewTurnAnswerStatus.NOT_REQUIRED,
                "Tiếp theo, mình muốn hỏi thêm một tình huống.");
        InterviewConversationTurn currentTurn = turn(
                session,
                currentQuestion,
                4,
                InterviewTurnType.CORE_QUESTION,
                InterviewTurnAnswerStatus.WAITING,
                "Bạn đã xử lý API chậm như thế nào?");
        InterviewConversationTurn futureTurn = turn(
                session,
                futureQuestion,
                5,
                InterviewTurnType.CORE_QUESTION,
                InterviewTurnAnswerStatus.WAITING,
                "NỘI DUNG TURN TƯƠNG LAI KHÔNG ĐƯỢC LỘ");
        InterviewConversationTurn detachedCurrentTurn = mock(InterviewConversationTurn.class);
        when(detachedCurrentTurn.getId()).thenReturn(currentTurn.getId());
        when(detachedCurrentTurn.getSequenceNo()).thenThrow(new LazyInitializationException("no Session"));
        when(detachedCurrentTurn.getAnswerStatus()).thenThrow(new LazyInitializationException("no Session"));
        session.setCurrentTurn(detachedCurrentTurn);

        List<UUID> sessionIds = List.of(session.getId());
        when(questionRepository.findBySessionIdInOrderBySessionIdAscOrderIndexAsc(sessionIds))
                .thenReturn(List.of(completedQuestion, currentQuestion, futureQuestion));
        when(answerRepository.findBySessionIdInOrderBySessionIdAscAnsweredAtAsc(sessionIds))
                .thenReturn(List.of(completedAnswer));
        when(answerFeedbackRepository.findByAnswerIdIn(List.of(completedAnswer.getId()))).thenReturn(List.of());
        when(sessionFeedbackRepository.findBySessionIdIn(sessionIds)).thenReturn(List.of());
        when(conversationTurnRepository.findBySessionIdInOrderBySessionIdAscSequenceNoAsc(sessionIds))
                .thenReturn(List.of(completedTurn, acknowledgement, transition, currentTurn, futureTurn));
        when(answerCaptureRepository.findByAnswerIdIn(List.of(completedAnswer.getId())))
                .thenReturn(List.of(correction));

        AiInterviewSessionResponse response = assembler.assemble(session);

        assertThat(response.questions()).isEmpty();
        assertThat(response.conversation().dialogueState()).isEqualTo("WAITING_ANSWER");
        assertThat(response.conversation().version()).isEqualTo(7);
        assertThat(response.conversation().currentTurnId()).isEqualTo(currentTurn.getId().toString());
        assertThat(response.conversation().expectsAnswer()).isTrue();
        verify(detachedCurrentTurn, never()).getSequenceNo();
        verify(detachedCurrentTurn, never()).getAnswerStatus();
        assertThat(response.conversation().completedCoreQuestions()).isEqualTo(1);
        assertThat(response.conversation().totalCoreQuestions()).isEqualTo(5);
        assertThat(response.conversation().speechText()).isEqualTo(
                "Được rồi.\nTiếp theo, mình muốn hỏi thêm một tình huống.\nBạn đã xử lý API chậm như thế nào?");
        assertThat(response.conversation().timeline()).hasSize(4);
        assertThat(response.conversation().timeline().get(0).finalTranscript())
                .isEqualTo("Em đã xây dựng REST API.");
        assertThat(response.conversation().timeline().get(0).transcriptCorrection().status())
                .isEqualTo("CORRECTED");
        assertThat(response.conversation().timeline().get(0).transcriptCorrection().candidateDecision())
                .isEqualTo("PENDING");
        assertThat(response.conversation().timeline().get(0).transcriptCorrection().corrections())
                .hasSize(1);
        assertThat(response.conversation().timeline().get(3).current()).isTrue();
        assertThat(response.conversation().errorCode()).isEqualTo("AI_PROVIDER_FAILED");

        String responseJson = new ObjectMapper().writeValueAsString(response);
        assertThat(responseJson)
                .doesNotContain("turnType")
                .doesNotContain("competency")
                .doesNotContain("rubric")
                .doesNotContain("bars")
                .doesNotContain("TƯƠNG LAI")
                .doesNotContain("secret-");
    }

    private InterviewQuestion question(InterviewSession session, int orderIndex, String content) {
        InterviewQuestion question = new InterviewQuestion();
        question.setId(UUID.randomUUID());
        question.setSession(session);
        question.setOrderIndex(orderIndex);
        question.setQuestionType("behavioral");
        question.setContent(content);
        question.setSourceType("AI_GENERATED");
        return question;
    }

    private InterviewConversationTurn turn(InterviewSession session,
                                           InterviewQuestion assessmentItem,
                                           int sequence,
                                           InterviewTurnType turnType,
                                           InterviewTurnAnswerStatus answerStatus,
                                           String text) {
        InterviewConversationTurn turn = new InterviewConversationTurn();
        turn.setId(UUID.randomUUID());
        turn.setSession(session);
        turn.setAssessmentItem(assessmentItem);
        turn.setSequenceNo(sequence);
        turn.setTurnType(turnType);
        turn.setAnswerStatus(answerStatus);
        turn.setText(text);
        turn.setCreatedAt(LocalDateTime.of(2026, 8, 14, 9, sequence));
        return turn;
    }
}
