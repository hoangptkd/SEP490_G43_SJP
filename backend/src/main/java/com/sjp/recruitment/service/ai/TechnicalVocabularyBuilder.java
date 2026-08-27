package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class TechnicalVocabularyBuilder {
    private final AiInterviewProperties properties;

    public TechnicalVocabularyBuilder(AiInterviewProperties properties) {
        this.properties = properties;
    }

    public GladiaTranscriptionContext build(InterviewSession session, InterviewQuestion question) {
        Map<String, String> unique = new LinkedHashMap<>();
        addAll(unique, properties.getGladiaBaseVocabulary());
        add(unique, session.getTitle());
        add(unique, question.getSkillTag());
        if (session.getJob() != null) {
            add(unique, session.getJob().getTitle());
            addAll(unique, session.getJob().getSkills());
        }
        Map<String, Object> practice = session.getPracticeContext();
        if (practice != null) {
            add(unique, practice.get("targetRole"));
            add(unique, practice.get("role"));
            add(unique, practice.get("skills"));
            add(unique, practice.get("technicalStack"));
        }
        extractQuestionTerms(unique, question.getContent());
        return new GladiaTranscriptionContext(unique.values().stream()
                .limit(Math.max(1, properties.getGladiaCustomVocabularyMaxItems()))
                .toList());
    }

    private void extractQuestionTerms(Map<String, String> output, String question) {
        if (question == null) return;
        for (String token : question.split("[^\\p{L}\\p{N}+#.]+")) {
            if (token.length() >= 2 && (token.matches(".*[A-Z+#].*") || token.matches(".*\\d.*"))) {
                add(output, token);
            }
        }
    }

    private void add(Map<String, String> output, Object value) {
        if (value instanceof Collection<?> values) {
            values.forEach(item -> add(output, item));
            return;
        }
        if (value == null) return;
        String text = String.valueOf(value).trim().replaceAll("\\s+", " ");
        if (text.length() < 2 || text.length() > 100) return;
        output.putIfAbsent(text.toLowerCase(Locale.ROOT), text);
    }

    private void addAll(Map<String, String> output, Collection<?> values) {
        if (values != null) values.forEach(value -> add(output, value));
    }
}
