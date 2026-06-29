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

    private BigDecimal salaryMin;
    private BigDecimal salaryMax;

    private String location;

    private String employerId;
}
