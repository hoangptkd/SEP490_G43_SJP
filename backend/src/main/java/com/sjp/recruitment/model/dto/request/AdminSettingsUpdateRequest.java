package com.sjp.recruitment.model.dto.request;

import java.util.Map;

public record AdminSettingsUpdateRequest(
        Map<String, String> settings
) {
}
