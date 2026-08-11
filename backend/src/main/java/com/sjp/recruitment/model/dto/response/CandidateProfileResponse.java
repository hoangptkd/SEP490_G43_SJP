package com.sjp.recruitment.model.dto.response;

import java.time.LocalDate;
import java.util.List;
import com.sjp.recruitment.model.dto.profile.CertificationItem;
import com.sjp.recruitment.model.dto.profile.EducationItem;
import com.sjp.recruitment.model.dto.profile.ProjectItem;
import com.sjp.recruitment.model.dto.profile.WorkExperienceItem;

public record CandidateProfileResponse(
        String id,
        String userId,
        String fullName,
        String phone,
        LocalDate dateOfBirth,
        Integer age,
        String location,
        String bio,
        List<String> skills,
        String headline,
        Integer experienceYears,
        String experienceLevel,
        String linkedinUrl,
        String portfolioUrl,
        List<EducationItem> education,
        List<WorkExperienceItem> workExperience,
        List<ProjectItem> projects,
        List<CertificationItem> certifications,
        boolean applyReady,
        List<String> missingReadinessItems
) {
}
