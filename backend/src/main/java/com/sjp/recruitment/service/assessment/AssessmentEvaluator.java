package com.sjp.recruitment.service.assessment;

import com.sjp.recruitment.model.AssessmentQuestion;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AssessmentEvaluator {

    /**
     * Evaluate assessment answers and calculate score
     */
    public Map<String, Object> evaluateAssessment(
            List<AssessmentQuestion> questions,
            List<AssessmentAnswer> answers) {

        int correctAnswers = 0;
        double totalScore = 0;

        for (AssessmentQuestion question : questions) {
            AssessmentAnswer answer = answers.stream()
                    .filter(a -> a.getQuestionId() == question.getId())
                    .findFirst()
                    .orElse(null);

            if (answer != null && question.isCorrect(answer.getAnswer())) {
                correctAnswers++;
                totalScore += question.getPoints();
            }
        }

        int totalQuestions = questions.size();
        double percentage = (double) correctAnswers / totalQuestions * 100;

        Map<String, Object> result = new HashMap<>();
        result.put("score", totalScore);
        result.put("correctAnswers", correctAnswers);
        result.put("totalQuestions", totalQuestions);
        result.put("percentage", percentage);
        result.put("feedback", generateFeedback(percentage));

        return result;
    }

    private String generateFeedback(double percentage) {
        if (percentage >= 90) return "Excellent! Outstanding performance.";
        if (percentage >= 80) return "Great job! Well done.";
        if (percentage >= 70) return "Good effort. Keep improving.";
        if (percentage >= 60) return "Satisfactory. There's room for improvement.";
        return "Needs improvement. Consider additional study.";
    }
}

class AssessmentAnswer {
    private Long questionId;
    private Object answer;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Object getAnswer() { return answer; }
    public void setAnswer(Object answer) { this.answer = answer; }
}