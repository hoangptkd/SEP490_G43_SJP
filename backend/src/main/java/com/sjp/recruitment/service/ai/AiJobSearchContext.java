package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.model.entity.CandidateCv;
import com.sjp.recruitment.model.entity.CandidateProfile;

import java.util.List;
import java.util.Map;

public record AiJobSearchContext(
        CandidateProfile candidate,
        CandidateCv defaultCv,
        String inputHash,
        boolean lowConfidence,
        List<String> skills,
        String headline,
        String bio,
        String location,
        Integer experienceYears,
        String experienceLevel,
        List<?> education,
        List<?> workExperience,
        List<?> projects,
        List<?> certifications,
        String cvText,
        Map<String, Object> providerContext
) {
}
