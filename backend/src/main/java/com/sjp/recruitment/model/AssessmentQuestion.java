package com.sjp.recruitment.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentQuestion {
    private Long id;
    private String text;
    private String type;
    private String[] options;
    private Object correctAnswer;
    private String difficultyLevel;
    private double points;

    public boolean isCorrect(Object userAnswer) {
        if (this.correctAnswer == null) return false;
        return this.correctAnswer.toString().equals(userAnswer.toString());
    }
}