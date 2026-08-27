package com.sjp.recruitment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    private SystemSettingsService settings;

    @BeforeEach
    void setUp() {
        settings = new SystemSettingsService(jdbc);
    }

    @Test
    void getString_returnsStoredValue() {
        stubSetting("SRP Portal");

        assertEquals("SRP Portal", settings.getString(SystemSettingsService.SITE_NAME, "fallback"));
    }

    @Test
    void getString_returnsDefaultWhenBlank() {
        stubSetting("  ");

        assertEquals("fallback", settings.getString("site_name", "fallback"));
    }

    @Test
    void getBoolean_acceptsTruthyValues() {
        stubSetting("yes");

        assertTrue(settings.getBoolean("flag", false));
    }

    @Test
    void getBoolean_acceptsFalsyValues() {
        stubSetting("0");

        assertFalse(settings.getBoolean("flag", true));
    }

    @Test
    void getBoolean_fallsBackWhenUnrecognized() {
        stubSetting("maybe");

        assertTrue(settings.getBoolean("flag", true));
    }

    @Test
    void getInt_parsesQuotedNumber() {
        stubSetting("\"12\"");

        assertEquals(12, settings.getInt("max", 1));
    }

    @Test
    void getInt_fallsBackOnInvalidNumber() {
        stubSetting("abc");

        assertEquals(7, settings.getInt("max", 7));
    }

    @Test
    void defaultPaymentProvider_acceptsKnownGateways() {
        stubSetting("VnPay");

        assertEquals("vnpay", settings.defaultPaymentProvider());
    }

    @Test
    void defaultPaymentProvider_fallsBackToPayos() {
        stubSetting("stripe");

        assertEquals("payos", settings.defaultPaymentProvider());
    }

    @Test
    void publicSnapshot_includesSiteAndThemeDefaultsAfterBlankLoad() {
        stubSetting("");

        Map<String, String> snapshot = settings.publicSnapshot();

        assertEquals("Smart Recruitment Portal", snapshot.get(SystemSettingsService.SITE_NAME));
        assertEquals("light", snapshot.get(SystemSettingsService.THEME_MODE));
        assertEquals("#00507d", snapshot.get(SystemSettingsService.THEME_PRIMARY_COLOR));
        assertEquals("false", snapshot.get(SystemSettingsService.MAINTENANCE_MODE));
        assertEquals("true", snapshot.get(SystemSettingsService.AI_INTERVIEW_ENABLED));
    }

    @Test
    void clearCache_reloadsNextRead() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class)))
                .thenReturn("true", "false");

        assertTrue(settings.isMaintenanceMode());
        settings.clearCache();
        assertFalse(settings.isMaintenanceMode());
    }

    @Test
    void clearCache_keyRemovesOnlyThatEntry() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class)))
                .thenReturn("on", "off");

        assertTrue(settings.getBoolean("ai_interview_enabled", false));
        settings.clearCache("ai_interview_enabled");
        assertFalse(settings.isAiInterviewEnabled());
    }

    @SuppressWarnings("unchecked")
    private void stubSetting(String value) {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class)))
                .thenReturn(value);
    }
}
