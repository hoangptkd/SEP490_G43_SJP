package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record JobAlertRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 160) String keyword,
        @Size(max = 160) String location,
        @Size(max = 120) String category,
        @Pattern(regexp = "^$|full_time|part_time|contract|internship|freelance") String jobType,
        @Pattern(regexp = "^$|onsite|remote|hybrid") String workMode,
        @DecimalMin("0") BigDecimal minSalary,
        @DecimalMin("0") BigDecimal maxSalary,
        @Pattern(regexp = "DAILY|WEEKLY") String frequency,
        Boolean enabled
) {
}
