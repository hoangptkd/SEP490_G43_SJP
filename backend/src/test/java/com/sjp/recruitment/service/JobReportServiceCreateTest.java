package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.JobReportRequest;
import com.sjp.recruitment.model.dto.response.UserResponse;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobReportServiceCreateTest {

    @Mock private AuthService authService;
    @Mock private UserRepository userRepository;
    @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Mock private AdminOpsService adminOpsService;

    @InjectMocks private JobReportService jobReportService;

    @Test
    void reportJob_rejectsNonCandidate() {
        when(authService.getCurrentUserResponse()).thenReturn(
                new UserResponse(UUID.randomUUID().toString(), "employer@srp.test", "EMPLOYER", "ACTIVE", true)
        );

        ApiException ex = assertThrows(ApiException.class,
                () -> jobReportService.reportJob(UUID.randomUUID().toString(), new JobReportRequest("spam", null)));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void reportJob_rejectsInvalidJobId() {
        stubCandidate();

        ApiException ex = assertThrows(ApiException.class,
                () -> jobReportService.reportJob("not-uuid", new JobReportRequest("spam", null)));
        assertEquals("INVALID_ID", ex.getCode());
    }

    @Test
    void reportJob_requiresReason() {
        stubCandidate();

        ApiException ex = assertThrows(ApiException.class,
                () -> jobReportService.reportJob(UUID.randomUUID().toString(), new JobReportRequest(" ", null)));
        assertEquals("REASON_REQUIRED", ex.getCode());
    }

    @Test
    void reportJob_rejectsUnknownReason() {
        stubCandidate();

        ApiException ex = assertThrows(ApiException.class,
                () -> jobReportService.reportJob(UUID.randomUUID().toString(), new JobReportRequest("hate", null)));
        assertEquals("INVALID_REASON", ex.getCode());
    }

    @Test
    void reportJob_notFound() {
        stubCandidate();
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class)))
                .thenReturn(null);

        ApiException ex = assertThrows(ApiException.class,
                () -> jobReportService.reportJob(UUID.randomUUID().toString(), new JobReportRequest("spam", "mô tả")));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void reportJob_rejectsNonPublicJob() {
        stubCandidate();
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class)))
                .thenReturn("draft");

        ApiException ex = assertThrows(ApiException.class,
                () -> jobReportService.reportJob(UUID.randomUUID().toString(), new JobReportRequest("scam", null)));
        assertEquals("JOB_NOT_PUBLIC", ex.getCode());
    }

    private void stubCandidate() {
        User candidate = new User();
        candidate.setId(UUID.randomUUID());
        candidate.setRole(User.UserRole.CANDIDATE);
        when(authService.getCurrentUserResponse()).thenReturn(
                new UserResponse(candidate.getId().toString(), "candidate@srp.test", "CANDIDATE", "ACTIVE", true)
        );
        when(userRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
    }
}
