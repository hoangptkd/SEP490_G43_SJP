package com.sjp.recruitment.model.dto.profile;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;
import java.util.Map;

public record EducationItem(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 160) String organization,
        @Size(max = 80) String time,
        @Size(max = 1000) String description
) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static EducationItem fromJson(Map<String, Object> values) {
        if (values == null) {
            return new EducationItem("", null, null, null);
        }

        String major = firstText(values, "major", "field");
        String title = firstText(values, "title", "degree", "major", "field");
        String organization = firstText(values, "organization", "school", "institution");
        String time = firstText(values, "time");
        if (time == null) {
            String start = firstText(values, "startYear", "startDate");
            String end = firstText(values, "endYear", "endDate", "graduationYear");
            time = joinPeriod(start, end);
        }
        if (time == null) {
            time = firstText(values, "graduationYear");
        }

        String description = firstText(values, "description");
        if (major != null && !containsIgnoreCase(title, major)
                && !containsIgnoreCase(description, major)) {
            String majorDescription = "Chuyên ngành: " + major;
            description = description == null
                    ? majorDescription
                    : description + "\n" + majorDescription;
        }

        return new EducationItem(title == null ? "" : title, organization, time, description);
    }

    private static String firstText(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value == null) continue;
            String text = String.valueOf(value).trim();
            if (!text.isBlank()) return text;
        }
        return null;
    }

    private static String joinPeriod(String start, String end) {
        if (start == null) return end;
        if (end == null) return start;
        return start + " - " + end;
    }

    private static boolean containsIgnoreCase(String value, String expected) {
        if (value == null || expected == null) return false;
        return value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }
}
