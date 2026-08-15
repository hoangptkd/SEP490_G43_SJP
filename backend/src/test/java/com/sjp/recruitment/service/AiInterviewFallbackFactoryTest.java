package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiInterviewFallbackFactoryTest {

    private final AiInterviewFallbackFactory factory = new AiInterviewFallbackFactory();

    @Test
    void initialPackageProvidesValidProfileAndThreeDistinctCoreCompetencies() {
        InterviewSession session = new InterviewSession();
        session.setTitle("Practice: Java Backend Developer");
        session.setPracticeContext(Map.of(
                "targetRole", "Java Backend Developer",
                "seniority", "fresher"));

        ShopAiKeyClient.InterviewPackageDraft result = factory.initialPackage(session);

        assertThat(result.evaluationProfile().targetRole()).isEqualTo("Java Backend Developer");
        assertThat(result.evaluationProfile().scoredCompetencyIds()).hasSize(3);
        assertThat(result.questions()).hasSize(3);
        assertThat(result.questions().stream().map(ShopAiKeyClient.RubricQuestionDraft::competencyId))
                .containsExactlyInAnyOrderElementsOf(result.evaluationProfile().scoredCompetencyIds());
    }

    @Test
    void adaptiveFallbackCoversMissingScoredCompetencyAndFillsRemainingQuestions() {
        InterviewSession session = new InterviewSession();
        session.setEvaluationProfile(Map.of(
                "scoredCompetencyIds", List.of(
                        "problem-solving", "technical-depth", "communication", "leadership"),
                "competencies", List.of(
                        Map.of("id", "problem-solving", "name", "Giải quyết vấn đề"),
                        Map.of("id", "technical-depth", "name", "Chiều sâu kỹ thuật"),
                        Map.of("id", "communication", "name", "Giao tiếp"),
                        Map.of("id", "leadership", "name", "Lãnh đạo"))));
        List<InterviewQuestion> existing = List.of(
                question(1, "problem-solving"),
                question(2, "technical-depth"),
                question(3, "communication"));

        List<ShopAiKeyClient.RubricQuestionDraft> result =
                factory.adaptiveQuestions(session, existing, 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).competencyId()).isEqualTo("leadership");
        assertThat(result).allSatisfy(item -> {
            assertThat(item.question()).endsWith("?");
            assertThat(item.expectedEvidence()).isNotEmpty();
        });
    }

    @Test
    void answerAnalysisFallbackAlwaysAdvancesAndPreservesPreviousSummary() {
        InterviewQuestion question = question(1, "problem-solving");
        question.setQuestionType("technical");

        ShopAiKeyClient.AnswerAnalysisDraft result = factory.answerAnalysis(
                question, Map.of("summary", "Evidence đã ghi nhận trước đó."));

        assertThat(result.action()).isEqualTo(ShopAiKeyClient.AnswerAnalysisAction.NEXT);
        assertThat(result.followUp()).isNull();
        assertThat(result.updatedItemSummary()).isEqualTo("Evidence đã ghi nhận trước đó.");
        assertThat(result.globalEvidenceDelta().weakEvidence()).isEmpty();
        assertThat(result.evidenceCoverage()).containsKeys(
                "accuracy", "reasoning", "tradeOffs", "implementationDetail", "realWorldApplication");
    }

    @Test
    void finalEvaluationFallbackReturnsOneConservativeRatingPerAnsweredQuestion() {
        InterviewSession session = new InterviewSession();
        InterviewQuestion question = question(1, "problem-solving");
        question.setSession(session);
        InterviewAnswer answer = new InterviewAnswer();
        answer.setQuestionId(question.getId());
        answer.setFinalTranscript("Tôi kiểm tra log, đo truy vấn, thêm index và kiểm thử lại thời gian phản hồi bằng Postman.");

        ShopAiKeyClient.InterviewEvaluationDraft result = factory.finalEvaluation(
                List.of(), List.of(question), List.of(answer));

        assertThat(result.questionRatings()).hasSize(1);
        assertThat(result.questionRatings().get(0).questionId()).isEqualTo(question.getId().toString());
        assertThat(result.questionRatings().get(0).barsLevel()).isBetween(1, 4);
        assertThat(result.actionPlan()).isNotEmpty();
    }

    @Test
    void fallbackRatingsAreCompatibleWithDeterministicScoreCalculator() {
        List<String> competencyIds = List.of("technical-foundation", "problem-solving", "communication");
        InterviewSession session = new InterviewSession();
        Map<String, Object> profile = Map.of(
                "scoredCompetencyIds", competencyIds,
                "voiceWeight", 0.20,
                "competencies", competencyIds.stream()
                        .map(id -> Map.<String, Object>of(
                                "id", id,
                                "scoredWeight", 1.0 / competencyIds.size()))
                        .toList());
        session.setEvaluationProfile(profile);
        List<InterviewQuestion> questions = List.of(
                question(1, competencyIds.get(0)),
                question(2, competencyIds.get(1)),
                question(3, competencyIds.get(2)));
        List<InterviewAnswer> answers = questions.stream().map(item -> {
            item.setSession(session);
            InterviewAnswer answer = new InterviewAnswer();
            answer.setQuestionId(item.getId());
            answer.setFinalTranscript("Tôi mô tả bối cảnh, phân tích nguyên nhân, thực hiện giải pháp và kiểm tra lại kết quả sau thay đổi.");
            answer.setAnsweredAt(LocalDateTime.now());
            return answer;
        }).toList();
        ShopAiKeyClient.InterviewEvaluationDraft evaluation =
                factory.finalEvaluation(List.of(), questions, answers);

        AiInterviewScoreCalculator.ScoreResult result = new AiInterviewScoreCalculator(
                new AiInterviewProperties()).calculate(
                profile, questions, answers, evaluation.questionRatings(), List.of());

        assertThat(result.questionResults()).hasSize(3);
        assertThat(result.overallScore()).isNotNull();
    }

    private InterviewQuestion question(int orderIndex, String competencyId) {
        InterviewQuestion question = new InterviewQuestion();
        question.setId(UUID.randomUUID());
        question.setOrderIndex(orderIndex);
        question.setQuestionType("general");
        question.setCompetencyId(competencyId);
        question.setContent("Câu hỏi " + orderIndex + "?");
        question.setRubric(Map.of());
        return question;
    }
}
