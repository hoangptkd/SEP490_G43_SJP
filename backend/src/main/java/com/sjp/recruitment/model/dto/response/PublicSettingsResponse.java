package com.sjp.recruitment.model.dto.response;

import java.util.Map;

public record PublicSettingsResponse(
        String siteName,
        String supportEmail,
        boolean maintenanceMode,
        boolean aiInterviewEnabled,
        boolean paymentGatewayEnabled,
        String paymentGatewayProvider,
        String themeMode,
        String themePrimaryColor
) {
    public static PublicSettingsResponse from(Map<String, String> snapshot) {
        return new PublicSettingsResponse(
                snapshot.getOrDefault("site_name", "Smart Recruitment Portal"),
                snapshot.getOrDefault("support_email", "support@sjp.local"),
                Boolean.parseBoolean(snapshot.getOrDefault("maintenance_mode", "false")),
                Boolean.parseBoolean(snapshot.getOrDefault("ai_interview_enabled", "true")),
                Boolean.parseBoolean(snapshot.getOrDefault("payment_gateway_enabled", "true")),
                snapshot.getOrDefault("payment_gateway_provider", "bank_transfer"),
                snapshot.getOrDefault("theme_mode", "light"),
                snapshot.getOrDefault("theme_primary_color", "#00507d")
        );
    }
}
