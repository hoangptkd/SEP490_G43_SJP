package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.JobReportRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobControllerAuthorizationTest {

    @Test
    void jobReportsRequireCandidateRole() throws Exception {
        var method = JobController.class.getMethod("reportJob", String.class, JobReportRequest.class);
        var policy = method.getAnnotation(PreAuthorize.class);

        assertEquals("hasRole('CANDIDATE')", policy.value());
    }
}
