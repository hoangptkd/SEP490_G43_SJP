package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.model.entity.CandidateCv;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.entity.Job;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptCorrectionContextBuilderTest {

    @Test
    void preservesSourcePriorityDeduplicatesTermsAndBoundsPreviousContext() {
        AiInterviewProperties properties = new AiInterviewProperties();
        properties.setTranscriptCorrectionMaxContextTerms(6);
        properties.setTranscriptCorrectionMaxPreviousContextChars(80);
        properties.setGladiaBaseVocabulary(List.of("Docker", "Redis", "Postman"));
        TranscriptCorrectionContextBuilder builder =
                new TranscriptCorrectionContextBuilder(properties, new ObjectMapper());

        CandidateProfile candidate = new CandidateProfile();
        candidate.setSkills(List.of("Fallback skill"));
        Job job = new Job();
        job.setTitle("Backend Developer");
        InterviewSession session = new InterviewSession();
        session.setCandidate(candidate);
        session.setJob(job);
        session.setPracticeContext(Map.of(
                "cvSkills", List.of("Spring Boot", "Postman", "spring boot"),
                "focusSkills", List.of("Docker", "REST API")));
        session.setEvidenceSummaryJson(Map.of(
                "interestingClaims", List.of("đã xử lý sự cố production bằng log và metric")));
        InterviewQuestion question = new InterviewQuestion();
        question.setContent("Bạn dùng Postman để kiểm thử API như thế nào?");
        question.setSkillTag("Postman");

        TranscriptCorrectionContext result = builder.build(
                session,
                question,
                "  em dùng   post men  ",
                Map.of("summary", "đã mô tả hành động nhưng chưa nêu kết quả"));

        assertThat(result.rawTranscript()).isEqualTo("em dùng post men");
        assertThat(result.cvTechnicalTerms()).containsExactly("Spring Boot", "Postman");
        assertThat(result.jobTechnicalTerms()).containsExactly("Backend Developer");
        assertThat(result.relevantTechnicalVocabulary()).containsExactly("Docker", "REST API", "Redis");
        assertThat(result.previousContext()).hasSizeLessThanOrEqualTo(80);
        assertThat(result.previousContext()).contains("currentItem");
        assertThat(result.cvTechnicalTerms()).doesNotContain("Fallback skill");
    }

    @Test
    void usesStructuredApplicationCvSkillsBeforeCandidateFallback() {
        AiInterviewProperties properties = new AiInterviewProperties();
        TranscriptCorrectionContextBuilder builder =
                new TranscriptCorrectionContextBuilder(properties, new ObjectMapper());
        CandidateProfile candidate = new CandidateProfile();
        candidate.setSkills(List.of("Candidate fallback"));
        CandidateCv cv = new CandidateCv();
        cv.setSnapshot(Map.of("skills", List.of("Java", "Spring Boot")));
        Application application = new Application();
        application.setCv(cv);
        InterviewSession session = new InterviewSession();
        session.setCandidate(candidate);
        session.setApplication(application);
        InterviewQuestion question = new InterviewQuestion();
        question.setContent("Hãy mô tả dự án backend của bạn?");

        TranscriptCorrectionContext result = builder.build(
                session, question, "em dùng java", Map.of());

        assertThat(result.cvTechnicalTerms()).containsExactly("Java", "Spring Boot");
        assertThat(result.cvTechnicalTerms()).doesNotContain("Candidate fallback");
    }
}
