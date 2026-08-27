package com.sjp.recruitment.model.dto.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EducationItemJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsLegacySeedShapeAndPreservesAllEducationDetails() throws Exception {
        String json = """
                {
                  "major": "Công nghệ thông tin",
                  "degree": "Cử nhân",
                  "school": "Đại học Công nghệ",
                  "startYear": 2018,
                  "endYear": 2022
                }
                """;

        EducationItem item = objectMapper.readValue(json, EducationItem.class);

        assertThat(item).isEqualTo(new EducationItem(
                "Cử nhân",
                "Đại học Công nghệ",
                "2018 - 2022",
                "Chuyên ngành: Công nghệ thông tin"
        ));
    }

    @Test
    void readsAlternativeLegacyShapeWithoutDuplicatingMajor() throws Exception {
        String json = """
                {
                  "institution": "FPT University",
                  "field": "Software Engineering",
                  "degree": "Software Engineering",
                  "startDate": "2019",
                  "graduationYear": "2023"
                }
                """;

        EducationItem item = objectMapper.readValue(json, EducationItem.class);

        assertThat(item.title()).isEqualTo("Software Engineering");
        assertThat(item.organization()).isEqualTo("FPT University");
        assertThat(item.time()).isEqualTo("2019 - 2023");
        assertThat(item.description()).isNull();
    }

    @Test
    void alwaysSerializesOnlyCanonicalFields() throws Exception {
        EducationItem item = objectMapper.readValue("""
                {
                  "school": "Đại học Công nghệ",
                  "degree": "Cử nhân",
                  "major": "Công nghệ thông tin",
                  "startYear": 2018,
                  "endYear": 2022
                }
                """, EducationItem.class);

        Map<String, Object> serialized = objectMapper.readValue(
                objectMapper.writeValueAsString(item),
                new TypeReference<>() {
                }
        );

        assertThat(serialized).containsOnlyKeys("title", "organization", "time", "description");
        assertThat(serialized).doesNotContainKeys(
                "school", "degree", "major", "startYear", "endYear", "institution");
    }

    @Test
    void keepsCanonicalShapeUnchanged() throws Exception {
        String json = """
                {
                  "title": "Kỹ sư phần mềm",
                  "organization": "Đại học FPT",
                  "time": "2019 - 2023",
                  "description": "Tốt nghiệp loại giỏi"
                }
                """;

        EducationItem item = objectMapper.readValue(json, EducationItem.class);

        assertThat(item).isEqualTo(new EducationItem(
                "Kỹ sư phần mềm",
                "Đại học FPT",
                "2019 - 2023",
                "Tốt nghiệp loại giỏi"
        ));
    }
}
