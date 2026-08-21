package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureLimitServiceLogicTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    private FeatureLimitService service;

    @BeforeEach
    void setUp() {
        service = new FeatureLimitService(jdbc, new ObjectMapper());
    }

    @Test
    void featureInt_readsNumericJsonValue() {
        assertEquals(15, service.featureInt("{\"maxJobs\":15}", "maxJobs", 3));
    }

    @Test
    void featureInt_readsNumericTextValue() {
        assertEquals(8, service.featureInt("{\"maxCv\":\"8\"}", "maxCv", 3));
    }

    @Test
    void featureInt_fallsBackToDefaultWhenMissing() {
        assertEquals(5, service.featureInt("{\"other\":1}", "maxJobs", 5));
        assertEquals(5, service.featureInt(null, "maxJobs", 5));
        assertEquals(5, service.featureInt("{}", null, 5));
    }

    @Test
    void featureIntOrNull_returnsNullForInvalidJson() {
        assertNull(service.featureIntOrNull("not-json", "maxJobs"));
        assertNull(service.featureIntOrNull("{\"maxJobs\":\"abc\"}", "maxJobs"));
    }

    @Test
    void isFeatured_usesPriorityThresholdOfOne() {
        assertFalse(FeatureLimitService.isFeatured(0));
        assertTrue(FeatureLimitService.isFeatured(1));
        assertTrue(FeatureLimitService.isFeatured(3));
    }

    @Test
    void hasActivePaidPlan_returnsFalseForMissingUser() {
        assertFalse(service.hasActivePaidPlan(null));
        assertFalse(service.hasActivePaidPlan(new User()));
    }

    @Test
    void hasActivePaidPlan_returnsFalseWhenNoActiveSubscription() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        User user = new User();
        user.setId(UUID.randomUUID());

        assertFalse(service.hasActivePaidPlan(user));
    }

    @Test
    void resolveListingPriorityForUser_returnsZeroWithoutPlan() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        assertEquals(0, service.resolveListingPriorityForUser(null));
        assertEquals(0, service.resolveListingPriorityForUser(UUID.randomUUID()));
    }

    @Test
    void resolveListingPrioritiesForUsers_returnsEmptyForBlankInput() {
        assertEquals(Map.of(), service.resolveListingPrioritiesForUsers(null));
        assertEquals(Map.of(), service.resolveListingPrioritiesForUsers(List.of()));
    }

    @Test
    void featureInt_negativeUnlimitedStyleValueIsPreserved() {
        assertEquals(-1, service.featureInt("{\"maxJobs\":-1}", "maxJobs", 3));
    }
}
