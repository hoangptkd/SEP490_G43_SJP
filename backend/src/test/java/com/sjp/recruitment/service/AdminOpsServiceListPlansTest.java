package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.AdminPlanResponse;
import com.sjp.recruitment.model.dto.response.UserResponse;
import com.sjp.recruitment.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOpsServiceListPlansTest {

    @Mock private AuthService authService;
    @Mock private UserRepository userRepository;
    @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Mock private BillingService billingService;
    @Mock private SystemSettingsService systemSettingsService;
    @Mock private FeatureLimitService featureLimitService;

    private AdminOpsService adminOpsService;

    @BeforeEach
    void setUp() {
        adminOpsService = new AdminOpsService(
                authService,
                userRepository,
                namedParameterJdbcTemplate,
                billingService,
                systemSettingsService,
                featureLimitService,
                new ObjectMapper()
        );
    }

    @Test
    void listPlans_rejectsNonAdmin() {
        when(authService.getCurrentUserResponse()).thenReturn(
                new UserResponse(UUID.randomUUID().toString(), "user@srp.test", "CANDIDATE", "ACTIVE", true)
        );

        ApiException ex = assertThrows(ApiException.class, () -> adminOpsService.listPlans("active"));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void listPlans_returnsAllPlansWhenStatusIsBlank() {
        stubAdmin();
        List<AdminPlanResponse> expected = List.of(plan("Plus"));
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(expected);

        assertSame(expected, adminOpsService.listPlans(null));
        verify(namedParameterJdbcTemplate).query(
                argThat(sql -> sql.contains("FROM plans") && !sql.contains("WHERE LOWER(status)")),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)
        );
    }

    @Test
    void listPlans_filtersByStatus() {
        stubAdmin();
        List<AdminPlanResponse> expected = List.of(plan("Premium"));
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(expected);

        assertSame(expected, adminOpsService.listPlans("ACTIVE"));
        verify(namedParameterJdbcTemplate).query(
                argThat(sql -> sql.contains("WHERE LOWER(status) = :status")),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)
        );
    }

    private void stubAdmin() {
        when(authService.getCurrentUserResponse()).thenReturn(
                new UserResponse(UUID.randomUUID().toString(), "admin@srp.test", "ADMIN", "ACTIVE", true)
        );
    }

    private static AdminPlanResponse plan(String name) {
        return new AdminPlanResponse(
                UUID.randomUUID().toString(), name, "employer", name, BigDecimal.TEN, "VND",
                30, "{}", "active", 1, null, null
        );
    }
}
