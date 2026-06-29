package com.sjp.recruitment.model.dto.response;

import java.util.List;

public record CandidateProfileResponse(
        String id,
        String userId,
        String fullName,
        String phone,
        String location,
        String bio,
        List<String> skills,
        List<Object> education,
        List<Object> workExperience,
        List<Object> projects,
        List<Object> certifications,
        boolean applyReady
) {
}
