package com.sjp.recruitment.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSnapshot {
    private String title;
    private String description;
    private List<String> requirements;
    private String benefits;
    private String location;
    private Integer vacancies;
    private String workingTime;
    private String salaryType;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String currency;
    private String jobType;
    private String workMode;
    private String experienceLevel;
    private LocalDate deadline;

    public static JobSnapshot fromJob(com.sjp.recruitment.model.entity.Job job) {
        if (job == null) return null;
        return JobSnapshot.builder()
                .title(job.getTitle())
                .description(job.getDescription())
                .requirements(job.getRequirements())
                .benefits(job.getBenefits())
                .location(job.getLocation())
                .vacancies(job.getVacancies())
                .workingTime(job.getWorkingTime())
                .salaryType(job.getSalaryType())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .currency(job.getCurrency())
                .jobType(job.getJobType())
                .workMode(job.getWorkMode())
                .experienceLevel(job.getExperienceLevel())
                .deadline(job.getDeadline())
                .build();
    }
}

