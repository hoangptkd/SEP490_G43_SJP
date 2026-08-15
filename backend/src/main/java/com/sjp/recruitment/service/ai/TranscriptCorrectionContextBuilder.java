package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.entity.CandidateCv;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class TranscriptCorrectionContextBuilder {

    private final AiInterviewProperties properties;
    private final ObjectMapper objectMapper;

    public TranscriptCorrectionContextBuilder(AiInterviewProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public TranscriptCorrectionContext build(
            InterviewSession session,
            InterviewQuestion question,
            String rawTranscript,
            Map<String, Object> itemEvidenceSummary
    ) {
        int remaining = Math.max(0, properties.getTranscriptCorrectionMaxContextTerms());
        Set<String> seenTerms = new java.util.LinkedHashSet<>();

        LinkedHashMap<String, String> cvTerms = new LinkedHashMap<>();
        Object practiceCvSkills = value(session.getPracticeContext(), "cvSkills");
        add(cvTerms, practiceCvSkills);
        if (cvTerms.isEmpty() && session.getApplication() != null) {
            CandidateCv cv = session.getApplication().getCv();
            if (cv != null && cv.getSnapshot() != null) {
                add(cvTerms, cv.getSnapshot().get("skills"));
            }
        }
        if (cvTerms.isEmpty() && session.getCandidate() != null) {
            add(cvTerms, session.getCandidate().getSkills());
        }
        List<String> boundedCvTerms = take(cvTerms, remaining, seenTerms);
        remaining -= boundedCvTerms.size();

        LinkedHashMap<String, String> jobTerms = new LinkedHashMap<>();
        if (session.getJob() != null) {
            add(jobTerms, session.getJob().getTitle());
            add(jobTerms, session.getJob().getSkills());
        }
        List<String> boundedJobTerms = take(jobTerms, remaining, seenTerms);
        remaining -= boundedJobTerms.size();

        LinkedHashMap<String, String> vocabulary = new LinkedHashMap<>();
        add(vocabulary, question == null ? null : question.getSkillTag());
        add(vocabulary, value(session.getPracticeContext(), "focusSkills"));
        add(vocabulary, properties.getGladiaBaseVocabulary());
        List<String> boundedVocabulary = take(vocabulary, remaining, seenTerms);

        return new TranscriptCorrectionContext(
                normalize(question == null ? null : question.getContent()),
                normalize(rawTranscript),
                boundedCvTerms,
                boundedJobTerms,
                boundedVocabulary,
                previousContext(session.getEvidenceSummaryJson(), itemEvidenceSummary)
        );
    }

    private String previousContext(Map<String, Object> sessionSummary, Map<String, Object> itemSummary) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (itemSummary != null && !itemSummary.isEmpty()) context.put("currentItem", itemSummary);
        if (sessionSummary != null && !sessionSummary.isEmpty()) context.put("session", sessionSummary);
        if (context.isEmpty()) return "";
        try {
            String value = objectMapper.writeValueAsString(context);
            int maxLength = Math.max(0, properties.getTranscriptCorrectionMaxPreviousContextChars());
            return maxLength == 0 ? "" : value.substring(0, Math.min(value.length(), maxLength));
        } catch (JsonProcessingException exception) {
            return "";
        }
    }

    private Object value(Map<String, Object> source, String key) {
        return source == null ? null : source.get(key);
    }

    private void add(Map<String, String> output, Object raw) {
        if (raw == null) return;
        if (raw instanceof Collection<?> values) {
            values.forEach(value -> add(output, value));
            return;
        }
        if (raw instanceof Map<?, ?> value) {
            Object candidate = value.containsKey("name") ? value.get("name")
                    : value.containsKey("skill") ? value.get("skill")
                    : value.get("title");
            add(output, candidate);
            return;
        }
        String text = normalize(String.valueOf(raw));
        if (text.length() < 2 || text.length() > 100) return;
        output.putIfAbsent(text.toLowerCase(Locale.ROOT), text);
    }

    private List<String> take(
            LinkedHashMap<String, String> values,
            int limit,
            Set<String> seenTerms
    ) {
        if (limit <= 0) return List.of();
        return values.entrySet().stream()
                .filter(entry -> seenTerms.add(entry.getKey()))
                .map(Map.Entry::getValue)
                .limit(limit)
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
