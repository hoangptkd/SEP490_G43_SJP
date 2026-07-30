package com.sjp.recruitment.model.dto.request;

import java.time.LocalDate;
import java.util.List;

public record CandidateProfileRequest(
        String fullName,
        String phone,
        LocalDate dateOfBirth,
        String location,
        String bio,
        List<String> skills,
        List<Object> education,
        List<Object> workExperience,
        List<Object> projects,
        List<Object> certifications
) {
}
