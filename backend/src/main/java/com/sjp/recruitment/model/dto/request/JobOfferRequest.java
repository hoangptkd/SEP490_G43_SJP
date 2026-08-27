package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record JobOfferRequest(
        @NotBlank(message = "Chức danh không được để trống")
        String positionTitle,

        BigDecimal salary,

        String salaryCurrency,

        String salaryType,

        LocalDate startDate,

        String benefits,

        String workingLocation,

        String offerLetterUrl,

        String employerNote
) {
}
