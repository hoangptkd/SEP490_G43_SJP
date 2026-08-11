package com.sjp.recruitment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiJobSearchControllerAuthorizationTest {
    @Test
    void allAiJobSearchEndpointsRequireCandidateRole() {
        PreAuthorize policy = AiJobSearchController.class.getAnnotation(PreAuthorize.class);
        assertEquals("hasRole('CANDIDATE')", policy.value());
    }
}
