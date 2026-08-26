package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.model.dto.request.AiJobSearchFilters;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiJobSearchCandidateSelectorTest {
    private final JobRepository repository = mock(JobRepository.class);
    private final AiJobMatchScorer scorer = new AiJobMatchScorer();
    private final AiJobSearchCandidateSelector selector = new AiJobSearchCandidateSelector(repository, new AiJobSearchProperties(), scorer);
    private final AiJobSearchContext context = mock(AiJobSearchContext.class);
    private final AiJobSearchFilters empty = AiJobSearchFilters.empty();

    @BeforeEach
    void setup() {
        when(context.cvText()).thenReturn("Python Django developer building REST APIs with PostgreSQL");
        when(context.inputHash()).thenReturn("cv-hash");
    }

    @Test
    void usesCvTextAndKeepsBroadPoolIncludingPartiallyMatchingJobs() {
        var jobs = IntStream.range(0, 40).mapToObj(i -> job("Python Developer " + i, "Hà Nội")).toList();
        when(repository.findPublicJobsForAi(any(LocalDate.class))).thenReturn(jobs);
        var selected = selector.select(context, empty);
        assertEquals(30, selected.size());
        verify(context, never()).skills();
        verify(context, never()).headline();
        verify(context, never()).experienceLevel();
    }

    @Test
    void profileFallbackPoolPreservesFiltersWithoutCvRankingOrShortlistLimit() {
        var jobs = new java.util.ArrayList<>(IntStream.range(0, 35).mapToObj(i -> job("Java " + i, "Hà Nội")).toList());
        jobs.add(job("Python in another city", "Đà Nẵng"));
        when(repository.findPublicJobsForAi(any(LocalDate.class))).thenReturn(jobs);
        var filters = new AiJobSearchFilters("Hà Nội", null, null, "full_time", "hybrid");
        assertEquals(35, selector.eligibleJobs(filters).size());
        verifyNoInteractions(context);
    }

    @Test
    void appliesHardFiltersBeforeShortlisting() {
        var jobs = new java.util.ArrayList<>(IntStream.range(0, 35).mapToObj(i -> job("Python Developer " + i, "Hà Nội")).toList());
        Job target = job("Backend Developer", "Đà Nẵng");
        jobs.add(target);
        when(repository.findPublicJobsForAi(any(LocalDate.class))).thenReturn(jobs);
        var filters = new AiJobSearchFilters("Thành phố Đà Nẵng", new BigDecimal("15000000"), new BigDecimal("25000000"), "full_time", "hybrid");
        var result = selector.select(context, filters);
        assertEquals(List.of(target), result.stream().map(AiJobSearchCandidateSelector.SelectedJob::job).toList());
    }

    @Test
    void cvWithJavaScriptDoesNotMatchJavaSkillBySubstring() {
        when(context.cvText()).thenReturn("JavaScript");
        Job javaJob = job("", "");
        when(javaJob.getDescription()).thenReturn("");
        when(javaJob.getRequirementsText()).thenReturn("");
        when(javaJob.getSkills()).thenReturn(List.of("Java"));
        assertEquals(0, scorer.shortlistScore(context, javaJob));
    }

    @Test
    void remoteFilterUsesWorkModeAndDoesNotDependOnLocationLabel() {
        Job remote = job("Python Developer", "Hà Nội");
        when(remote.getWorkMode()).thenReturn("remote");
        Job onsite = job("Python Developer", "Hà Nội");
        when(repository.findPublicJobsForAi(any(LocalDate.class))).thenReturn(List.of(remote, onsite));
        assertEquals(List.of(remote), selector.select(context, new AiJobSearchFilters("Từ xa", null, null, null, null))
                .stream().map(AiJobSearchCandidateSelector.SelectedJob::job).toList());
    }

    @Test
    void hashChangesForFiltersAndJobInputsUsedByAi() {
        Job job = job("Python Developer", "Hà Nội");
        var selected = List.of(new AiJobSearchCandidateSelector.SelectedJob(job, 70));
        String original = selector.evaluationHash(context, selected, empty);
        assertNotEquals(original, selector.evaluationHash(context, selected,
                new AiJobSearchFilters("Hà Nội", null, null, null, null)));
        when(job.getSalaryMax()).thenReturn(new BigDecimal("40000000"));
        String salaryChanged = selector.evaluationHash(context, selected, empty);
        assertNotEquals(original, salaryChanged);
        when(job.getRequirementsText()).thenReturn("Updated requirements");
        assertNotEquals(salaryChanged, selector.evaluationHash(context, selected, empty));
    }

    private Job job(String title, String location) {
        Job job = mock(Job.class);
        when(job.getId()).thenReturn(UUID.randomUUID());
        when(job.getTitle()).thenReturn(title);
        when(job.getSkills()).thenReturn(List.of("Python", "Docker"));
        when(job.getLocation()).thenReturn(location);
        when(job.getJobType()).thenReturn("full_time");
        when(job.getWorkMode()).thenReturn("hybrid");
        when(job.getSalaryMin()).thenReturn(new BigDecimal("20000000"));
        when(job.getSalaryMax()).thenReturn(new BigDecimal("30000000"));
        return job;
    }
}
