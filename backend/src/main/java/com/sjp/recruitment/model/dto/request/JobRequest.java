package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class JobRequest {
    @NotBlank
    private String title;

    private String description;

    private List<String> requirements;
    private List<String> skills;
    private String benefits;

    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String salaryType;

    private String location;
    private String companyLocationId;

    private Integer vacancies;
    private String workingTime;
    private String jobType;
    private String workMode;
    private String experienceLevel;
    private String deadline;
    private String status;

    private String employerId;
    
    private com.fasterxml.jackson.databind.JsonNode rankingConfig;
}
