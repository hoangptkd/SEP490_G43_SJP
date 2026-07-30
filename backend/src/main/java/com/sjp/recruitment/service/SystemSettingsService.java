package com.sjp.recruitment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SystemSettingsService {

    public static final String SITE_NAME = "site_name";
    public static final String SUPPORT_EMAIL = "support_email";
    public static final String MAINTENANCE_MODE = "maintenance_mode";
    public static final String COMPANY_REVIEW_REQUIRED = "company_review_required";
    public static final String AI_INTERVIEW_ENABLED = "ai_interview_enabled";
    public static final String AI_SYSTEM_PROMPT = "ai_system_prompt";
    public static final String AI_FEEDBACK_PROMPT = "ai_feedback_prompt";
    public static final String PAYMENT_GATEWAY_ENABLED = "payment_gateway_enabled";
    public static final String PAYMENT_GATEWAY_PROVIDER = "payment_gateway_provider";
    public static final String THEME_MODE = "theme_mode";
    public static final String THEME_PRIMARY_COLOR = "theme_primary_color";

    private final NamedParameterJdbcTemplate jdbc;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public String getString(String key, String defaultValue) {
        String value = cache.computeIfAbsent(key, this::loadValue);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String raw = getString(key, null);
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace("\"", "");
        if (isTruthy(normalized)) {
            return true;
        }
        if (isFalsy(normalized)) {
            return false;
        }
        return defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String raw = getString(key, null);
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim().replace("\"", ""));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public boolean isMaintenanceMode() {
        return getBoolean(MAINTENANCE_MODE, false);
    }

    public boolean isCompanyReviewRequired() {
        return getBoolean(COMPANY_REVIEW_REQUIRED, true);
    }

    public boolean isAiInterviewEnabled() {
        return getBoolean(AI_INTERVIEW_ENABLED, true);
    }

    public boolean isPaymentGatewayEnabled() {
        return getBoolean(PAYMENT_GATEWAY_ENABLED, true);
    }

    public String defaultPaymentProvider() {
        String provider = getString(PAYMENT_GATEWAY_PROVIDER, "bank_transfer").trim().toLowerCase(Locale.ROOT);
        if ("momo".equals(provider) || "vnpay".equals(provider) || "bank_transfer".equals(provider)) {
            return provider;
        }
        return "bank_transfer";
    }

    public void clearCache() {
        cache.clear();
    }

    public void clearCache(String key) {
        if (StringUtils.hasText(key)) {
            cache.remove(key.trim());
        }
    }

    public Map<String, String> publicSnapshot() {
        return Map.of(
                SITE_NAME, getString(SITE_NAME, "Smart Recruitment Portal"),
                SUPPORT_EMAIL, getString(SUPPORT_EMAIL, "support@sjp.local"),
                MAINTENANCE_MODE, String.valueOf(isMaintenanceMode()),
                AI_INTERVIEW_ENABLED, String.valueOf(isAiInterviewEnabled()),
                PAYMENT_GATEWAY_ENABLED, String.valueOf(isPaymentGatewayEnabled()),
                PAYMENT_GATEWAY_PROVIDER, defaultPaymentProvider(),
                THEME_MODE, getString(THEME_MODE, "light"),
                THEME_PRIMARY_COLOR, getString(THEME_PRIMARY_COLOR, "#00507d")
        );
    }

    private String loadValue(String key) {
        try {
            return jdbc.query("""
                            SELECT setting_value FROM system_settings WHERE setting_key = :key LIMIT 1
                            """,
                    new MapSqlParameterSource("key", key),
                    rs -> rs.next() ? rs.getString(1) : "");
        } catch (Exception ex) {
            return "";
        }
    }

    private static boolean isTruthy(String value) {
        return "true".equals(value) || "1".equals(value) || "yes".equals(value) || "on".equals(value);
    }

    private static boolean isFalsy(String value) {
        return "false".equals(value) || "0".equals(value) || "no".equals(value) || "off".equals(value);
    }
}
