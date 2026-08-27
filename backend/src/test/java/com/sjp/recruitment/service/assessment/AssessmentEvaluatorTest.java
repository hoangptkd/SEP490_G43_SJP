package com.sjp.recruitment.service.assessment;

import com.sjp.recruitment.model.AssessmentQuestion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssessmentEvaluatorTest {

    private final AssessmentEvaluator evaluator = new AssessmentEvaluator();

    @Test
    void evaluateAssessment_allCorrect_returnsExcellent() {
        Map<String, Object> result = evaluator.evaluateAssessment(
                List.of(question(1, "A", 10), question(2, "B", 10)),
                List.of(answer(1, "A"), answer(2, "B"))
        );

        assertEquals(20.0, result.get("score"));
        assertEquals(2, result.get("correctAnswers"));
        assertEquals(2, result.get("totalQuestions"));
        assertEquals(100.0, result.get("percentage"));
        assertEquals("Excellent! Outstanding performance.", result.get("feedback"));
    }

    @Test
    void evaluateAssessment_eightyPercent_returnsGreatJob() {
        Map<String, Object> result = evaluator.evaluateAssessment(
                List.of(question(1, "A", 5), question(2, "B", 5), question(3, "C", 5), question(4, "D", 5), question(5, "E", 5)),
                List.of(answer(1, "A"), answer(2, "B"), answer(3, "C"), answer(4, "D"), answer(5, "X"))
        );

        assertEquals(20.0, result.get("score"));
        assertEquals(4, result.get("correctAnswers"));
        assertEquals(80.0, result.get("percentage"));
        assertEquals("Great job! Well done.", result.get("feedback"));
    }

    @Test
    void evaluateAssessment_seventyPercent_returnsGoodEffort() {
        Map<String, Object> result = evaluator.evaluateAssessment(
                List.of(question(1, "A", 1), question(2, "B", 1), question(3, "C", 1),
                        question(4, "D", 1), question(5, "E", 1), question(6, "F", 1),
                        question(7, "G", 1), question(8, "H", 1), question(9, "I", 1), question(10, "J", 1)),
                List.of(answer(1, "A"), answer(2, "B"), answer(3, "C"), answer(4, "D"), answer(5, "E"),
                        answer(6, "F"), answer(7, "G"), answer(8, "X"), answer(9, "X"), answer(10, "X"))
        );

        assertEquals(70.0, result.get("percentage"));
        assertEquals("Good effort. Keep improving.", result.get("feedback"));
    }

    @Test
    void evaluateAssessment_sixtyPercent_returnsSatisfactory() {
        Map<String, Object> result = evaluator.evaluateAssessment(
                List.of(question(1, "A", 2), question(2, "B", 2), question(3, "C", 2),
                        question(4, "D", 2), question(5, "E", 2)),
                List.of(answer(1, "A"), answer(2, "B"), answer(3, "C"), answer(4, "X"), answer(5, "X"))
        );

        assertEquals(6.0, result.get("score"));
        assertEquals(60.0, result.get("percentage"));
        assertEquals("Satisfactory. There's room for improvement.", result.get("feedback"));
    }

    @Test
    void evaluateAssessment_belowSixty_returnsNeedsImprovement() {
        Map<String, Object> result = evaluator.evaluateAssessment(
                List.of(question(1, "A", 10), question(2, "B", 10)),
                List.of(answer(1, "Z"), answer(2, "Y"))
        );

        assertEquals(0.0, result.get("score"));
        assertEquals(0, result.get("correctAnswers"));
        assertEquals(0.0, result.get("percentage"));
        assertEquals("Needs improvement. Consider additional study.", result.get("feedback"));
    }

    @Test
    void evaluateAssessment_missingAnswer_countsAsIncorrect() {
        Map<String, Object> result = evaluator.evaluateAssessment(
                List.of(question(1, "A", 5), question(2, "B", 5)),
                List.of(answer(1, "A"))
        );

        assertEquals(5.0, result.get("score"));
        assertEquals(1, result.get("correctAnswers"));
        assertEquals(50.0, result.get("percentage"));
    }

    @Test
    void evaluateAssessment_comparesAnswersAsStrings() {
        AssessmentQuestion numeric = question(1, 42, 3);
        Map<String, Object> result = evaluator.evaluateAssessment(
                List.of(numeric),
                List.of(answer(1, "42"))
        );

        assertEquals(1, result.get("correctAnswers"));
        assertEquals(3.0, result.get("score"));
    }

    @Test
    void evaluateAssessment_nullCorrectAnswer_neverMatches() {
        AssessmentQuestion question = question(1, null, 8);
        Map<String, Object> result = evaluator.evaluateAssessment(
                List.of(question),
                List.of(answer(1, "anything"))
        );

        assertEquals(0, result.get("correctAnswers"));
        assertEquals(0.0, result.get("score"));
    }

    private static AssessmentQuestion question(long id, Object correct, double points) {
        AssessmentQuestion question = new AssessmentQuestion();
        question.setId(id);
        question.setCorrectAnswer(correct);
        question.setPoints(points);
        return question;
    }

    private static AssessmentAnswer answer(long questionId, Object value) {
        AssessmentAnswer answer = new AssessmentAnswer();
        answer.setQuestionId(questionId);
        answer.setAnswer(value);
        return answer;
    }
}
