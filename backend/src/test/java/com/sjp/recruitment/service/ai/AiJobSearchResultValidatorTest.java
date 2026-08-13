package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.model.entity.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AiJobSearchResultValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiJobSearchResultValidator validator;
    private Job job;
    private AiJobSearchContext context;

    @BeforeEach
    void setUp() {
        validator = new AiJobSearchResultValidator();

        job = new Job();
        job.setId(UUID.randomUUID());
        job.setTitle("Java Developer");
        context = new AiJobSearchContext(
                null, null, "hash", false, List.of("Java"), "Java Developer", "", "Hà Nội",
                2, "JUNIOR", List.of(), List.of(), List.of(), List.of(), "", Map.of()
        );
    }

    @Test
    void acceptsProviderReasonAndKeepsScorerBreakdown() throws Exception {
        var score = scoreBreakdown(91, List.of("Java"), List.of("AWS"));
        var json = objectMapper.readTree("""
                {"items":[{
                  "jobId":"%s",
                  "reason":"Kỹ năng Java phù hợp với yêu cầu công việc."
                }]}
                """.formatted(job.getId()));

        var result = validator.validate(
                json,
                context,
                List.of(new AiJobSearchCandidateSelector.SelectedJob(job, score))
        );

        assertEquals(1, result.size());
        assertEquals(List.of("Java"), result.get(0).matchedSkills());
        assertEquals(List.of("AWS"), result.get(0).missingSkills());
        assertEquals(91, result.get(0).matchScore());
        assertEquals("Kỹ năng Java phù hợp với yêu cầu công việc.", result.get(0).reason());
    }

    @Test
    void rejectsJobOutsideCandidatePool() throws Exception {
        var score = scoreBreakdown(80, List.of("Java"), List.of("AWS"));
        var json = objectMapper.readTree("""
                {"items":[{
                  "jobId":"%s",
                  "reason":"Phù hợp."
                }]}
                """.formatted(UUID.randomUUID()));

        assertThrows(AiJobSearchValidationException.class,
                () -> validator.validate(
                        json,
                        context,
                        List.of(new AiJobSearchCandidateSelector.SelectedJob(job, score))
                ));
    }

    private AiJobMatchScorer.ScoreBreakdown scoreBreakdown(int matchScore, List<String> matched, List<String> missing) {
        return new AiJobMatchScorer.ScoreBreakdown(
                matchScore, 0.8, 0.7, 0.6, 0.5, 0.4, matched, missing, false
        );
    }

    private AiJobMatchScorer.ScoreBreakdown score(int matchScore) {
        return new AiJobMatchScorer.ScoreBreakdown(
                matchScore, 20.0, 20.0, 15.0, 15.0, 10.0, List.of(), List.of(), false
        );
    }
}
