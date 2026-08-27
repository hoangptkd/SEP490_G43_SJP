package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.Past;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.sjp.recruitment.model.dto.profile.CertificationItem;
import com.sjp.recruitment.model.dto.profile.EducationItem;
import com.sjp.recruitment.model.dto.profile.ProjectItem;
import com.sjp.recruitment.model.dto.profile.WorkExperienceItem;

import java.time.LocalDate;
import java.util.List;

public record CandidateProfileRequest(
        @Size(max = 120) String fullName,
        @Size(max = 32) @Pattern(regexp = "^$|^[0-9+().\\s-]{8,32}$", message = "Số điện thoại không hợp lệ") String phone,
        @Past LocalDate dateOfBirth,
        @Size(max = 160) String location,
        @Size(max = 2000) String bio,
        @Size(max = 30) List<@Size(min = 1, max = 80) String> skills,
        @Size(max = 160) String headline,
        @Min(0) @Max(80) Integer experienceYears,
        @Pattern(regexp = "^$|intern|fresher|junior|middle|senior|lead|manager", message = "Cấp độ kinh nghiệm không hợp lệ") String experienceLevel,
        @Pattern(regexp = "^$|https?://.+", message = "LinkedIn phải bắt đầu bằng http:// hoặc https://") String linkedinUrl,
        @Pattern(regexp = "^$|https?://.+", message = "Portfolio phải bắt đầu bằng http:// hoặc https://") String portfolioUrl,
        @Valid @Size(max = 20) List<EducationItem> education,
        @Valid @Size(max = 30) List<WorkExperienceItem> workExperience,
        @Valid @Size(max = 30) List<ProjectItem> projects,
        @Valid @Size(max = 30) List<CertificationItem> certifications
) {
}
