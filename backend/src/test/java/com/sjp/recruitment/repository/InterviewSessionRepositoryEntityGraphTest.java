package com.sjp.recruitment.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewSessionRepositoryEntityGraphTest {

    @Test
    void responseFacingQueriesFetchJobSkills() throws NoSuchMethodException {
        assertFetchesJobSkills(
                "findByCandidateIdAndDeletedAtIsNullOrderByUpdatedAtDesc",
                UUID.class);
        assertFetchesJobSkills("findAllWithResponseDetailsByIdIn", java.util.List.class);
        assertFetchesJobSkills(
                "findByIdAndCandidateIdAndDeletedAtIsNull",
                UUID.class,
                UUID.class);
    }

    private void assertFetchesJobSkills(String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = InterviewSessionRepository.class.getMethod(methodName, parameterTypes);
        EntityGraph entityGraph = method.getAnnotation(EntityGraph.class);

        assertThat(entityGraph).isNotNull();
        assertThat(entityGraph.attributePaths())
                .contains("job.jobSkills", "job.jobSkills.skill");
    }
}
