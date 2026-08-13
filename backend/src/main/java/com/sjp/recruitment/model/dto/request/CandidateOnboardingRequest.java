package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CandidateOnboardingRequest(
        @NotNull @Size(min = 1, max = 5)
        List<@NotBlank @Size(max = 120) String> desiredJobTitles,
        @NotNull @DecimalMin("0.0") @Digits(integer = 12, fraction = 2)
        BigDecimal expectedSalary,
        @NotBlank
        @Pattern(regexp = "intern|fresher|junior|middle|senior|lead", message = "Cấp độ kinh nghiệm không hợp lệ")
        String experienceLevel,
        @NotNull @Size(min = 1, max = 5)
        List<@NotBlank @Size(max = 80) String> preferredLocations,
        boolean willingToRelocate
) {
}
