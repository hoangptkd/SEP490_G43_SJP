package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CandidateProfileRequest(
        @Size(max = 120) String fullName,
        @Size(max = 32) String phone,
        @Past LocalDate dateOfBirth,
        @Size(max = 160) String location,
        @Size(max = 2000) String bio,
        @Size(max = 30) List<@Size(min = 1, max = 80) String> skills,
        List<Object> education,
        List<Object> workExperience,
        List<Object> projects,
        List<Object> certifications
) {
}
