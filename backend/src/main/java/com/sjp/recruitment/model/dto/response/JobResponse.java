package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record JobResponse(
        String id,
        String title,
        String description,
        List<String> requirements,
        List<String> skills,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String location,
        String experienceLevel,
        LocalDateTime deadline,
        String status,
        CompanyResponse company,
        String companyLocationId,
        CompanyLocationResponse companyLocation,
        boolean saved,
        boolean applied,
        Integer matchScore,
        String benefits,
        Integer vacancies,
        String workingTime,
        String salaryType,
        String jobType,
        String workMode,
        Integer viewsCount,
        String rejectionReason,
        Long applicationsCount,
        LocalDateTime reportFixDeadline,
        com.fasterxml.jackson.databind.JsonNode rankingConfig
) {
}
