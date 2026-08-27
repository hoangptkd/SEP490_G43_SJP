package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record AiJobSearchFilters(
        @Size(max = 200) String location,
        @DecimalMin("0") BigDecimal minSalary,
        @DecimalMin("0") BigDecimal maxSalary,
        @Size(max = 50) String jobType,
        @Size(max = 50) String workMode
) {
    public AiJobSearchFilters {
        location = clean(location);
        jobType = clean(jobType);
        workMode = clean(workMode);
        minSalary = minSalary == null ? null : minSalary.stripTrailingZeros();
        maxSalary = maxSalary == null ? null : maxSalary.stripTrailingZeros();
    }

    public static AiJobSearchFilters empty() {
        return new AiJobSearchFilters(null, null, null, null, null);
    }

    @AssertTrue(message = "Mức lương từ không được lớn hơn mức lương đến.")
    @JsonIgnore
    public boolean isSalaryRangeValid() {
        return minSalary == null || maxSalary == null || minSalary.compareTo(maxSalary) <= 0;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
