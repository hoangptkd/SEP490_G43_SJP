package com.sjp.recruitment.model.entity;

import com.sjp.recruitment.model.dto.SubmittedResumeSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationResumeSnapshotTest {

    @Test
    void serializesBuilderDataSoLaterCvEditsDoNotMutateSubmittedSnapshot() {
        Map<String, Object> builderData = new LinkedHashMap<>();
        builderData.put("skills", new ArrayList<>(List.of("Java")));

        CvVersion version = new CvVersion();
        version.setId(UUID.randomUUID());
        version.setTitle("Backend CV");
        version.setTemplateKey("classic");
        version.setSnapshot(builderData);
        version.setUpdatedAt(LocalDateTime.parse("2026-08-11T01:00:00"));

        Application application = new Application();
        application.setResumeSnapshot(SubmittedResumeSnapshot.fromBuilder(version));

        builderData.put("skills", List.of("Java", "Kotlin"));

        assertEquals(List.of("Java"), application.getResumeSnapshot().builderSnapshot().get("skills"));
        assertEquals("Backend CV", application.getResumeSnapshot().title());
    }
}
