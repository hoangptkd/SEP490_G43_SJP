package com.sjp.recruitment.model.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EducationItem(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 160) String organization,
        @Size(max = 80) String time,
        @Size(max = 1000) String description
) {
}
