package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.model.entity.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiJobSearchResultValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AiJobSearchResultValidator validator = new AiJobSearchResultValidator(new AiJobSearchProperties());
    private AiJobSearchContext context;
    private Job job;

    @BeforeEach
    void setUp() {
        context = mock(AiJobSearchContext.class);
        when(context.cvText()).thenReturn("Built Java REST APIs and Spring Boot applications for ecommerce.");
        job = job("Java Backend Developer");
    }

    @Test
    void acceptsAiScoreAndEvidenceInsteadOfPreliminaryScore() {
        var result = validator.validate(response(item(job, 91)), context, List.of(selected(job, 20)));
        assertEquals(91, result.get(0).matchScore());
        assertEquals(List.of("Java"), result.get(0).matchedSkills());
        assertEquals(List.of("Docker"), result.get(0).missingSkills());
        assertEquals("Built Java REST APIs", result.get(0).evidence().get(0).cvQuote());
    }

    @Test
    void aiCanReverseThePreliminaryRanking() {
        Job other = job("Spring Boot Developer");
        var result = validator.validate(response(item(job, 40), item(other, 90)), context,
                List.of(selected(job, 99), selected(other, 10)));
        assertEquals(other.getId(), result.get(0).job().getId());
        assertEquals(1, result.get(0).rank());
        assertEquals(job.getId(), result.get(1).job().getId());
        assertEquals(2, result.get(1).rank());
    }

    @Test
    void rejectsJobsOutsideShortlistAndDuplicateIds() {
        ObjectNode foreign = item(job, 80).put("jobId", UUID.randomUUID().toString());
        assertThrows(AiJobSearchValidationException.class, () ->
                validator.validate(response(foreign), context, List.of(selected(job, 80))));
        Job other = job("Another backend job");
        assertThrows(AiJobSearchValidationException.class, () ->
                validator.validate(response(item(job, 80), item(job, 80)), context,
                        List.of(selected(job, 80), selected(other, 70))));
    }

    @Test
    void rejectsInvalidScoresMissingItemsAndFabricatedQuotes() {
        for (int score : new int[]{-1, 101}) assertInvalid(item(job, score));
        assertInvalid(item(job, 80).put("matchScore", "80"));
        ObjectNode fabricated = item(job, 80);
        ((ObjectNode) fabricated.withArray("evidence").get(0)).put("cvQuote", "Ten years as an engineering manager");
        assertInvalid(fabricated);
        ObjectNode fabricatedJob = item(job, 80);
        ((ObjectNode) fabricatedJob.withArray("evidence").get(0)).put("jobQuote", "Unlisted job requirement");
        assertInvalid(fabricatedJob);
        assertThrows(AiJobSearchValidationException.class, () ->
                validator.validate(response(), context, List.of(selected(job, 80))));
    }

    @Test
    void rejectsUnsupportedOrContradictorySkills() {
        ObjectNode unsupported = item(job, 80);
        unsupported.putArray("matchedSkills").add("Kubernetes");
        assertInvalid(unsupported);
        ObjectNode unevidenced = item(job, 80);
        unevidenced.putArray("matchedSkills").add("Docker");
        unevidenced.putArray("missingSkills");
        assertInvalid(unevidenced);
        ObjectNode contradictory = item(job, 80);
        contradictory.putArray("missingSkills").add("Java");
        assertInvalid(contradictory);
    }

    @Test
    void acceptsRestApiWhenUploadedCvUsesRestfulApis() {
        when(context.cvText()).thenReturn("Built Java applications with Spring Boot. Used RESTful APIs and MySQL.");
        when(job.getSkills()).thenReturn(List.of("Java", "REST API", "SQL"));
        ObjectNode result = item(job, 80);
        ((ObjectNode) result.withArray("evidence").get(0)).put("cvQuote", "Built Java applications");
        result.putArray("matchedSkills").add("Java").add("REST API");
        result.putArray("missingSkills").add("SQL");
        assertEquals(List.of("Java", "REST API"), validator.validate(response(result), context,
                List.of(selected(job, 80))).get(0).matchedSkills());
    }

    @Test
    void identifiesExactUnsupportedMatchedSkillWithoutLeakingContent() {
        when(context.cvText()).thenReturn("Built Java REST APIs using MySQL.");
        when(job.getSkills()).thenReturn(List.of("Java", "SQL", "Docker"));
        ObjectNode invalid = item(job, 80);
        invalid.putArray("matchedSkills").add("Java").add("SQL");
        var failure = assertThrows(AiJobSearchValidationException.class, () ->
                validator.validate(response(invalid), context, List.of(selected(job, 80))));
        assertEquals("MATCHED_SKILL_NOT_IN_CV", failure.getCode());
        assertEquals("items[0].matchedSkills[1]", failure.getPath());
        assertFalse(failure.getMessage().contains(context.cvText()));
    }

    @Test
    void skillAliasesCannotBeListedAsBothMatchedAndMissing() {
        when(job.getSkills()).thenReturn(List.of("Java", "REST API", "RESTful APIs"));
        ObjectNode invalid = item(job, 80);
        invalid.putArray("matchedSkills").add("REST API");
        invalid.putArray("missingSkills").add("RESTful APIs");
        var failure = assertThrows(AiJobSearchValidationException.class, () ->
                validator.validate(response(invalid), context, List.of(selected(job, 80))));
        assertEquals("INCONSISTENT_SKILL_GAP", failure.getCode());
        assertEquals("items[0].missingSkills[0]", failure.getPath());
        invalid.putArray("missingSkills");
        invalid.withArray("matchedSkills").add("RESTful APIs");
        assertInvalid(invalid);
    }

    @Test
    void skillMatchingPreservesTokenBoundariesAndProgrammingLanguageSymbols() {
        for (String[] pair : List.of(new String[]{"JavaScript", "Java"}, new String[]{"MySQL", "SQL"},
                new String[]{"C++", "C"}, new String[]{"C#", "C"}, new String[]{"C++", "C#"})) {
            when(context.cvText()).thenReturn("Experienced in " + pair[0]);
            when(job.getSkills()).thenReturn(List.of(pair[1]));
            ObjectNode invalid = item(job, 80);
            invalid.putArray("matchedSkills").add(pair[1]);
            invalid.putArray("missingSkills");
            var failure = assertThrows(AiJobSearchValidationException.class, () ->
                    validator.validate(response(invalid), context, List.of(selected(job, 80))));
            assertEquals("MATCHED_SKILL_NOT_IN_CV", failure.getCode());
        }
    }

    @Test
    void equivalentSkillNamesDoNotAllowParaphrasedEvidenceQuotes() {
        when(context.cvText()).thenReturn("Built Java applications with RESTful APIs.");
        ObjectNode invalid = item(job, 80);
        invalid.putArray("matchedSkills").add("REST API");
        ObjectNode evidence = (ObjectNode) invalid.withArray("evidence").get(0);
        evidence.put("cvQuote", "Built Java applications with REST API.");
        var failure = assertThrows(AiJobSearchValidationException.class, () ->
                validator.validate(response(invalid), context, List.of(selected(job, 80))));
        assertEquals("CV_QUOTE_NOT_FOUND", failure.getCode());
        evidence.put("cvQuote", "Built Java applications with RESTful APIs.");
        assertEquals(1, validator.validate(response(invalid), context, List.of(selected(job, 80))).size());
    }

    @Test
    void rejectsParaphrasedOrSplicedQuotesWithSafeFieldDiagnostics() {
        when(job.getRequirementsText()).thenReturn("Có 1 năm kinh nghiệm Java. Làm việc nhóm. Ưu tiên Docker.");
        for (String quote : List.of("Cần 1 năm kinh nghiệm Java.", "Có 1 năm kinh nghiệm Java. Ưu tiên Docker.")) {
            ObjectNode invalid = item(job, 80);
            ((ObjectNode) invalid.withArray("evidence").get(0)).put("jobQuote", quote);
            var exception = assertThrows(AiJobSearchValidationException.class, () ->
                    validator.validate(response(invalid), context, List.of(selected(job, 80))));
            assertEquals("JOB_QUOTE_NOT_FOUND", exception.getCode());
            assertEquals("items[0].evidence[0].jobQuote", exception.getPath());
            assertFalse(exception.getMessage().contains(quote));
        }
        ObjectNode corrected = item(job, 80);
        ((ObjectNode) corrected.withArray("evidence").get(0)).put("jobQuote", "Có 1 năm kinh nghiệm Java.");
        assertEquals(1, validator.validate(response(corrected), context, List.of(selected(job, 80))).size());
    }

    @Test
    void identifiesCvQuoteSeparatelyWithoutIncludingPrivateText() {
        ObjectNode invalid = item(job, 80);
        String privateText = "Private invented candidate experience";
        ((ObjectNode) invalid.withArray("evidence").get(0)).put("cvQuote", privateText);
        var exception = assertThrows(AiJobSearchValidationException.class, () ->
                validator.validate(response(invalid), context, List.of(selected(job, 80))));
        assertEquals("CV_QUOTE_NOT_FOUND", exception.getCode());
        assertEquals("items[0].evidence[0].cvQuote", exception.getPath());
        assertFalse(exception.getMessage().contains(privateText));
    }

    @Test
    void returnsOnlyTopTenFromBroaderShortlist() {
        var jobs = java.util.stream.IntStream.range(0, 30).mapToObj(i -> job("Java " + i)).toList();
        var shortlist = jobs.stream().map(j -> selected(j, 20)).toList();
        var items = jobs.subList(20, 30).stream().map(j -> item(j, 90)).toArray(ObjectNode[]::new);
        assertEquals(10, validator.validate(response(items), context, shortlist).size());
    }

    private void assertInvalid(ObjectNode item) {
        assertThrows(AiJobSearchValidationException.class, () ->
                validator.validate(response(item), context, List.of(selected(job, 80))));
    }

    private ObjectNode item(Job target, int score) {
        return mapper.valueToTree(Map.of("jobId", target.getId().toString(), "matchScore", score,
                "matchedSkills", List.of("Java"), "missingSkills", List.of("Docker"), "reason", "Kinh nghiệm Java phù hợp.",
                "evidence", List.of(Map.of("cvQuote", "Built Java REST APIs", "jobQuote", "Develop Java REST APIs"))));
    }

    private ObjectNode response(ObjectNode... items) {
        ObjectNode result = mapper.createObjectNode();
        var array = result.putArray("items");
        for (ObjectNode item : items) array.add(item);
        return result;
    }

    private Job job(String title) {
        Job value = mock(Job.class);
        when(value.getId()).thenReturn(UUID.randomUUID());
        when(value.getTitle()).thenReturn(title);
        when(value.getDescription()).thenReturn("Develop Java REST APIs using Spring Boot.");
        when(value.getRequirementsText()).thenReturn("Java and Docker");
        when(value.getSkills()).thenReturn(List.of("Java", "Docker"));
        return value;
    }

    private AiJobSearchCandidateSelector.SelectedJob selected(Job value, int score) {
        return new AiJobSearchCandidateSelector.SelectedJob(value, score);
    }
}
