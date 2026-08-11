package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.entity.Job;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalVocabularyBuilderTest {
    @Test
    void combinesConfigurationJobPracticeAndQuestionContextWithoutDuplicates() {
        AiInterviewProperties properties = new AiInterviewProperties();
        properties.setGladiaBaseVocabulary(List.of("Spring Boot", "JWT"));
        Job job = new Job();
        job.setTitle("Java Backend Engineer");
        InterviewSession session = new InterviewSession();
        session.setTitle("Backend interview");
        session.setJob(job);
        session.setPracticeContext(Map.of("skills", List.of("Kafka", "Spring Boot"), "targetRole", "Backend Developer"));
        InterviewQuestion question = new InterviewQuestion();
        question.setSkillTag("REST API");
        question.setContent("Bạn triển khai OAuth2 và PostgreSQL như thế nào?");

        List<String> vocabulary = new TechnicalVocabularyBuilder(properties).build(session, question).vocabulary();

        assertThat(vocabulary).contains("Spring Boot", "JWT", "Java Backend Engineer", "Kafka",
                "Backend Developer", "REST API", "OAuth2", "PostgreSQL");
        assertThat(vocabulary.stream().filter("Spring Boot"::equalsIgnoreCase)).hasSize(1);
    }
}
