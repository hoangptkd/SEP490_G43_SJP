package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.CompanyReviewRequest;
import com.sjp.recruitment.model.dto.response.AdminCompanyDetailResponse;
import com.sjp.recruitment.model.dto.response.AdminCompanyOwnerResponse;
import com.sjp.recruitment.model.dto.response.AdminCompanySummaryResponse;
import com.sjp.recruitment.model.dto.response.AdminDashboardResponse;
import com.sjp.recruitment.model.dto.response.AdminJobDetailResponse;
import com.sjp.recruitment.model.dto.response.AdminJobSummaryResponse;
import com.sjp.recruitment.model.dto.response.AdminStatItemResponse;
import com.sjp.recruitment.model.dto.response.AdminStatisticsResponse;
import com.sjp.recruitment.model.dto.response.AdminTrendPointResponse;
import com.sjp.recruitment.model.dto.response.AdminUserSummaryResponse;
import com.sjp.recruitment.model.dto.response.CompanyDocumentResponse;
import com.sjp.recruitment.model.dto.response.CompanyLocationResponse;
import com.sjp.recruitment.model.dto.response.CompanyProfileResponse;
import com.sjp.recruitment.model.dto.response.CompanyResponse;
import com.sjp.recruitment.model.dto.response.JobResponse;
import com.sjp.recruitment.model.dto.response.UserResponse;
import com.sjp.recruitment.model.entity.Company;
import com.sjp.recruitment.model.entity.CompanyDocument;
import com.sjp.recruitment.model.entity.Employer;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.model.dto.response.CompanyIndustryResponse;
import com.sjp.recruitment.repository.CompanyDocumentRepository;
import com.sjp.recruitment.repository.CompanyIndustryRepository;
import com.sjp.recruitment.repository.CompanyLocationRepository;
import com.sjp.recruitment.repository.CompanyRepository;
import com.sjp.recruitment.repository.EmployerRepository;
import com.sjp.recruitment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyDocumentRepository companyDocumentRepository;
    private final CompanyLocationRepository companyLocationRepository;
    private final CompanyIndustryRepository companyIndustryRepository;
    private final EmployerRepository employerRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final DtoMapper dtoMapper;
    private final AdminOpsService adminOpsService;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboardStats() {
        requireAdmin();

        long totalUsers = count("SELECT COUNT(*) FROM users WHERE status <> 'deleted'");
        long activeJobs = count("SELECT COUNT(*) FROM jobs WHERE status = 'published'");
        long pendingCompanies = count("SELECT COUNT(*) FROM companies WHERE verification_status = 'pending'");
        long pendingJobs = count("SELECT COUNT(*) FROM jobs WHERE status = 'pending_review'");
        long applicationsToday = count("SELECT COUNT(*) FROM applications WHERE applied_at::date = CURRENT_DATE");
        long verifiedCompanies = count("SELECT COUNT(*) FROM companies WHERE verification_status = 'verified'");
        long totalCompanies = count("SELECT COUNT(*) FROM companies");
        long totalApplications = count("SELECT COUNT(*) FROM applications");
        long totalEmployers = count("SELECT COUNT(*) FROM users WHERE role = 'employer' AND status <> 'deleted'");
        long totalCandidates = count("SELECT COUNT(*) FROM users WHERE role IN ('job_seeker', 'candidate') AND status <> 'deleted'");
        long closedJobs = count("SELECT COUNT(*) FROM jobs WHERE status = 'closed'");

        BigDecimal revenueToday = money("""
                SELECT COALESCE(SUM(amount), 0)
                FROM payments
                WHERE status = 'paid' AND COALESCE(paid_at, created_at)::date = CURRENT_DATE
                """);
        BigDecimal revenueMonth = money("""
                SELECT COALESCE(SUM(amount), 0)
                FROM payments
                WHERE status = 'paid'
                  AND date_trunc('month', COALESCE(paid_at, created_at)) = date_trunc('month', CURRENT_DATE)
                """);
        long paidCountMonth = count("""
                SELECT COUNT(*)
                FROM payments
                WHERE status = 'paid'
                  AND date_trunc('month', COALESCE(paid_at, created_at)) = date_trunc('month', CURRENT_DATE)
                """);
        long activeSubscriptions = count("SELECT COUNT(*) FROM subscriptions WHERE status = 'active'");

        MapSqlParameterSource empty = new MapSqlParameterSource();
        long interviewsToday = safeCount("""
                SELECT COUNT(*)
                FROM interview_sessions
                WHERE deleted_at IS NULL AND created_at::date = CURRENT_DATE
                """, empty);
        long interviewsWeek = safeCount("""
                SELECT COUNT(*)
                FROM interview_sessions
                WHERE deleted_at IS NULL
                  AND created_at >= date_trunc('week', CURRENT_DATE)
                  AND created_at < date_trunc('week', CURRENT_DATE) + INTERVAL '7 days'
                """, empty);
        long interviewsCompletedWeek = safeCount("""
                SELECT COUNT(*)
                FROM interview_sessions
                WHERE deleted_at IS NULL
                  AND status = 'completed'
                  AND created_at >= date_trunc('week', CURRENT_DATE)
                  AND created_at < date_trunc('week', CURRENT_DATE) + INTERVAL '7 days'
                """, empty);

        LocalDate trendEnd = LocalDate.now().plusDays(1);
        LocalDate trendStart = LocalDate.now().minusDays(6);
        MapSqlParameterSource trendParams = new MapSqlParameterSource()
                .addValue("start", trendStart)
                .addValue("end", trendEnd);

        return new AdminDashboardResponse(
                totalUsers,
                activeJobs,
                pendingCompanies + pendingJobs,
                applicationsToday,
                pendingCompanies,
                pendingJobs,
                verifiedCompanies,
                totalCompanies,
                totalApplications,
                totalEmployers,
                totalCandidates,
                revenueToday,
                revenueMonth,
                paidCountMonth,
                activeSubscriptions,
                interviewsToday,
                interviewsWeek,
                interviewsCompletedWeek,
                closedJobs,
                trendPoints(applicationTrendSql(true), trendParams),
                safeTrendPoints(revenueTrendSql(), trendParams),
                LocalDateTime.now()
        );
    }

    private BigDecimal money(String sql) {
        BigDecimal value = namedParameterJdbcTemplate.getJdbcTemplate().queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private String revenueTrendSql() {
        return """
                SELECT bucket::date::text AS date,
                       COALESCE(SUM(p.amount), 0)::bigint AS value
                FROM generate_series(CAST(:start AS date), CAST(:end AS date) - INTERVAL '1 day', INTERVAL '1 day') bucket
                LEFT JOIN payments p
                  ON COALESCE(p.paid_at, p.created_at)::date = bucket::date
                 AND p.status = 'paid'
                GROUP BY bucket
                ORDER BY bucket
                """;
    }

    private long count(String sql) {
        Long value = namedParameterJdbcTemplate.getJdbcTemplate().queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    @Transactional(readOnly = true)
    public AdminStatisticsResponse getStatistics(String period, Integer year, Integer month, String date) {
        requireAdmin();
        String resolvedPeriod = period == null ? "all" : period.trim().toLowerCase(Locale.ROOT);
        boolean allTime = "all".equals(resolvedPeriod) || "total".equals(resolvedPeriod);

        if (allTime) {
            LocalDate trendEnd = LocalDate.now().plusDays(1);
            LocalDate trendStart = LocalDate.now().withDayOfMonth(1).minusMonths(11);
            MapSqlParameterSource trendParams = new MapSqlParameterSource()
                    .addValue("start", trendStart)
                    .addValue("end", trendEnd);
            MapSqlParameterSource empty = new MapSqlParameterSource();

            return new AdminStatisticsResponse(
                    count("SELECT COUNT(*) FROM users WHERE status <> 'deleted'", empty),
                    count("SELECT COUNT(*) FROM companies", empty),
                    count("SELECT COUNT(*) FROM jobs", empty),
                    count("SELECT COUNT(*) FROM applications", empty),
                    count("SELECT COALESCE(SUM(views_count), 0) FROM jobs", empty),
                    statItems("""
                            SELECT role AS key, role AS label, COUNT(*) AS value
                            FROM users
                            WHERE status <> 'deleted'
                            GROUP BY role
                            ORDER BY value DESC
                            """, empty),
                    statItems("""
                            SELECT status AS key, status AS label, COUNT(*) AS value
                            FROM users
                            WHERE status <> 'deleted'
                            GROUP BY status
                            ORDER BY value DESC
                            """, empty),
                    statItems("""
                            SELECT verification_status AS key, verification_status AS label, COUNT(*) AS value
                            FROM companies
                            GROUP BY verification_status
                            ORDER BY value DESC
                            """, empty),
                    statItems("""
                            SELECT status AS key, status AS label, COUNT(*) AS value
                            FROM jobs
                            GROUP BY status
                            ORDER BY value DESC
                            """, empty),
                    statItems("""
                            SELECT status AS key, status AS label, COUNT(*) AS value
                            FROM applications
                            GROUP BY status
                            ORDER BY value DESC
                            """, empty),
                    trendPoints(userTrendSql(false, null), trendParams),
                    trendPoints(userTrendSql(false, "job_seeker"), trendParams),
                    trendPoints(userTrendSql(false, "employer"), trendParams),
                    trendPoints(applicationTrendSql(false), trendParams),
                    trendPoints(jobTrendSql(false), trendParams),
                    safeCount("SELECT COUNT(*) FROM interview_sessions WHERE deleted_at IS NULL", empty),
                    safeCount("SELECT COUNT(*) FROM interview_sessions WHERE deleted_at IS NULL AND status = 'completed'", empty),
                    safeCount("SELECT COUNT(*) FROM interview_sessions WHERE deleted_at IS NULL AND status = 'in_progress'", empty),
                    safeCount("SELECT COUNT(*) FROM interview_answers WHERE feedback_status = 'completed'", empty),
                    safeCount("SELECT COUNT(*) FROM ai_job_recommendations", empty),
                    safeCount("SELECT COUNT(*) FROM ai_ranking_jobs", empty),
                    safeAverage("SELECT COALESCE(AVG(overall_score), 0) FROM interview_sessions WHERE deleted_at IS NULL AND overall_score IS NOT NULL", empty),
                    safeStatItems("""
                            SELECT status AS key, status AS label, COUNT(*) AS value
                            FROM interview_sessions
                            WHERE deleted_at IS NULL
                            GROUP BY status
                            ORDER BY value DESC
                            """, empty),
                    safeTrendPoints(interviewTrendSql(false), trendParams),
                    LocalDateTime.now()
            );
        }

        TimeRange range = resolveStatisticsRange(resolvedPeriod, year, month, date);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("start", range.start())
                .addValue("end", range.end());

        return new AdminStatisticsResponse(
                count("SELECT COUNT(*) FROM users WHERE status <> 'deleted' AND created_at >= :start AND created_at < :end", params),
                count("SELECT COUNT(*) FROM companies WHERE created_at >= :start AND created_at < :end", params),
                count("SELECT COUNT(*) FROM jobs WHERE created_at >= :start AND created_at < :end", params),
                count("SELECT COUNT(*) FROM applications WHERE applied_at >= :start AND applied_at < :end", params),
                count("SELECT COALESCE(SUM(views_count), 0) FROM jobs WHERE created_at >= :start AND created_at < :end", params),
                statItems("""
                        SELECT role AS key, role AS label, COUNT(*) AS value
                        FROM users
                        WHERE status <> 'deleted'
                          AND created_at >= :start AND created_at < :end
                        GROUP BY role
                        ORDER BY value DESC
                        """, params),
                statItems("""
                        SELECT status AS key, status AS label, COUNT(*) AS value
                        FROM users
                        WHERE status <> 'deleted'
                          AND created_at >= :start AND created_at < :end
                        GROUP BY status
                        ORDER BY value DESC
                        """, params),
                statItems("""
                        SELECT verification_status AS key, verification_status AS label, COUNT(*) AS value
                        FROM companies
                        WHERE created_at >= :start AND created_at < :end
                        GROUP BY verification_status
                        ORDER BY value DESC
                        """, params),
                statItems("""
                        SELECT status AS key, status AS label, COUNT(*) AS value
                        FROM jobs
                        WHERE created_at >= :start AND created_at < :end
                        GROUP BY status
                        ORDER BY value DESC
                        """, params),
                statItems("""
                        SELECT status AS key, status AS label, COUNT(*) AS value
                        FROM applications
                        WHERE applied_at >= :start AND applied_at < :end
                        GROUP BY status
                        ORDER BY value DESC
                        """, params),
                trendPoints(userTrendSql(range.dailyBuckets(), null), params),
                trendPoints(userTrendSql(range.dailyBuckets(), "job_seeker"), params),
                trendPoints(userTrendSql(range.dailyBuckets(), "employer"), params),
                trendPoints(applicationTrendSql(range.dailyBuckets()), params),
                trendPoints(jobTrendSql(range.dailyBuckets()), params),
                safeCount("SELECT COUNT(*) FROM interview_sessions WHERE deleted_at IS NULL AND created_at >= :start AND created_at < :end", params),
                safeCount("SELECT COUNT(*) FROM interview_sessions WHERE deleted_at IS NULL AND status = 'completed' AND created_at >= :start AND created_at < :end", params),
                safeCount("SELECT COUNT(*) FROM interview_sessions WHERE deleted_at IS NULL AND status = 'in_progress' AND created_at >= :start AND created_at < :end", params),
                safeCount("SELECT COUNT(*) FROM interview_answers WHERE feedback_status = 'completed' AND created_at >= :start AND created_at < :end", params),
                safeCount("SELECT COUNT(*) FROM ai_job_recommendations WHERE created_at >= :start AND created_at < :end", params),
                safeCount("SELECT COUNT(*) FROM ai_ranking_jobs WHERE created_at >= :start AND created_at < :end", params),
                safeAverage("SELECT COALESCE(AVG(overall_score), 0) FROM interview_sessions WHERE deleted_at IS NULL AND overall_score IS NOT NULL AND created_at >= :start AND created_at < :end", params),
                safeStatItems("""
                        SELECT status AS key, status AS label, COUNT(*) AS value
                        FROM interview_sessions
                        WHERE deleted_at IS NULL
                          AND created_at >= :start AND created_at < :end
                        GROUP BY status
                        ORDER BY value DESC
                        """, params),
                safeTrendPoints(interviewTrendSql(range.dailyBuckets()), params),
                LocalDateTime.now()
        );
    }

    private long safeCount(String sql, MapSqlParameterSource params) {
        try {
            return count(sql, params);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double safeAverage(String sql, MapSqlParameterSource params) {
        try {
            Double value = namedParameterJdbcTemplate.queryForObject(sql, params, Double.class);
            return value == null ? 0 : value;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private List<AdminStatItemResponse> safeStatItems(String sql, MapSqlParameterSource params) {
        try {
            return statItems(sql, params);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<AdminTrendPointResponse> safeTrendPoints(String sql, MapSqlParameterSource params) {
        try {
            return trendPoints(sql, params);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String interviewTrendSql(boolean byDay) {
        if (byDay) {
            return """
                    SELECT bucket::date::text AS date, COUNT(s.id) AS value
                    FROM generate_series(CAST(:start AS date), CAST(:end AS date) - 1, interval '1 day') bucket
                    LEFT JOIN interview_sessions s
                      ON s.created_at::date = bucket::date
                     AND s.deleted_at IS NULL
                    GROUP BY bucket
                    ORDER BY bucket
                    """;
        }
        return """
                SELECT to_char(bucket, 'YYYY-MM-01') AS date, COUNT(s.id) AS value
                FROM generate_series(date_trunc('month', CAST(:start AS timestamp)), date_trunc('month', CAST(:end AS timestamp)) - interval '1 month', interval '1 month') bucket
                LEFT JOIN interview_sessions s
                  ON date_trunc('month', s.created_at) = bucket
                 AND s.deleted_at IS NULL
                GROUP BY bucket
                ORDER BY bucket
                """;
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    private List<AdminStatItemResponse> statItems(String sql, MapSqlParameterSource params) {
        return namedParameterJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new AdminStatItemResponse(
                        rs.getString("key"),
                        rs.getString("label"),
                        rs.getLong("value")
                )
        );
    }

    private List<AdminTrendPointResponse> trendPoints(String sql, MapSqlParameterSource params) {
        return namedParameterJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new AdminTrendPointResponse(
                        rs.getString("date"),
                        rs.getLong("value")
                )
        );
    }

    private TimeRange resolveStatisticsRange(String period, Integer year, Integer month, String date) {
        String resolvedPeriod = period == null ? "week" : period.trim().toLowerCase(Locale.ROOT);
        if ("year".equals(resolvedPeriod)) {
            int resolvedYear = year == null ? LocalDate.now().getYear() : year;
            LocalDate start = LocalDate.of(resolvedYear, 1, 1);
            return new TimeRange(start, start.plusYears(1), false);
        }

        int resolvedYear = year == null ? LocalDate.now().getYear() : year;
        if ("month".equals(resolvedPeriod)) {
            int resolvedMonth = month != null && month >= 1 && month <= 12 ? month : LocalDate.now().getMonthValue();
            YearMonth yearMonth = YearMonth.of(resolvedYear, resolvedMonth);
            LocalDate start = yearMonth.atDay(1);
            return new TimeRange(start, start.plusMonths(1), true);
        }

        LocalDate referenceDate;
        try {
            referenceDate = StringUtils.hasText(date) ? LocalDate.parse(date) : LocalDate.now();
        } catch (Exception exception) {
            referenceDate = LocalDate.now();
        }
        LocalDate start = referenceDate.minusDays(referenceDate.getDayOfWeek().getValue() - 1L);
        return new TimeRange(start, start.plusDays(7), true);
    }

    private String applicationTrendSql(boolean byDay) {
        if (byDay) {
            return """
                    SELECT bucket::date::text AS date, COUNT(a.id) AS value
                    FROM generate_series(CAST(:start AS date), CAST(:end AS date) - INTERVAL '1 day', INTERVAL '1 day') bucket
                    LEFT JOIN applications a ON a.applied_at::date = bucket::date
                    GROUP BY bucket
                    ORDER BY bucket
                    """;
        }
        return """
                SELECT bucket::date::text AS date, COUNT(a.id) AS value
                FROM generate_series(CAST(:start AS date), CAST(:end AS date) - INTERVAL '1 month', INTERVAL '1 month') bucket
                LEFT JOIN applications a ON a.applied_at >= bucket AND a.applied_at < bucket + INTERVAL '1 month'
                GROUP BY bucket
                ORDER BY bucket
                """;
    }

    private String userTrendSql(boolean byDay, String role) {
        String roleClause = role == null ? "" : " AND u.role = '" + role + "'\n";
        if (byDay) {
            return """
                    SELECT bucket::date::text AS date, COUNT(u.id) AS value
                    FROM generate_series(CAST(:start AS date), CAST(:end AS date) - INTERVAL '1 day', INTERVAL '1 day') bucket
                    LEFT JOIN users u ON u.created_at::date = bucket::date
                      AND u.status <> 'deleted'
                    """
                    + roleClause
                    + """
                    GROUP BY bucket
                    ORDER BY bucket
                    """;
        }
        return """
                SELECT bucket::date::text AS date, COUNT(u.id) AS value
                FROM generate_series(CAST(:start AS date), CAST(:end AS date) - INTERVAL '1 month', INTERVAL '1 month') bucket
                LEFT JOIN users u ON u.created_at >= bucket AND u.created_at < bucket + INTERVAL '1 month'
                  AND u.status <> 'deleted'
                """
                + roleClause
                + """
                GROUP BY bucket
                ORDER BY bucket
                """;
    }

    private String jobTrendSql(boolean byDay) {
        if (byDay) {
            return """
                    SELECT bucket::date::text AS date, COUNT(j.id) AS value
                    FROM generate_series(CAST(:start AS date), CAST(:end AS date) - INTERVAL '1 day', INTERVAL '1 day') bucket
                    LEFT JOIN jobs j ON j.created_at::date = bucket::date
                    GROUP BY bucket
                    ORDER BY bucket
                    """;
        }
        return """
                SELECT bucket::date::text AS date, COUNT(j.id) AS value
                FROM generate_series(CAST(:start AS date), CAST(:end AS date) - INTERVAL '1 month', INTERVAL '1 month') bucket
                LEFT JOIN jobs j ON j.created_at >= bucket AND j.created_at < bucket + INTERVAL '1 month'
                GROUP BY bucket
                ORDER BY bucket
                """;
    }

    private record TimeRange(LocalDate start, LocalDate end, boolean dailyBuckets) {
    }

    @Transactional(readOnly = true)
    public List<AdminUserSummaryResponse> listUsers(String role, String status) {
        requireAdmin();
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder("""
                SELECT
                    id::text AS id,
                    email,
                    full_name,
                    phone,
                    role,
                    status,
                    email_verified_at,
                    last_login_at,
                    created_at,
                    updated_at
                FROM users
                WHERE status <> 'deleted'
                """);

        if (StringUtils.hasText(role) && !"all".equalsIgnoreCase(role)) {
            sql.append(" AND LOWER(role) = :role\n");
            params.addValue("role", normalizeUserRole(role));
        }
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status)) {
            sql.append(" AND LOWER(status) = :status\n");
            params.addValue("status", status.trim().toLowerCase(Locale.ROOT));
        }
        sql.append("""
                ORDER BY
                    CASE role WHEN 'admin' THEN 1 WHEN 'employer' THEN 2 ELSE 3 END,
                    created_at DESC
                """);

        return namedParameterJdbcTemplate.query(sql.toString(), params, this::mapAdminUserSummary);
    }

    @Transactional
    public AdminUserSummaryResponse suspendUser(String id) {
        User currentAdmin = requireAdminUser();
        User user = findUser(id);
        if (user.getId().equals(currentAdmin.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_SUSPEND_SELF", "Bạn không thể tự khóa tài khoản của mình");
        }
        user.setStatus(User.UserStatus.SUSPENDED);
        return toAdminUserSummary(userRepository.save(user));
    }

    @Transactional
    public AdminUserSummaryResponse activateUser(String id) {
        requireAdmin();
        User user = findUser(id);
        user.setStatus(User.UserStatus.ACTIVE);
        return toAdminUserSummary(userRepository.save(user));
    }

    private String normalizeUserRole(String role) {
        return switch (role.trim().toLowerCase(Locale.ROOT)) {
            case "candidate", "job_seeker" -> "job_seeker";
            case "employer" -> "employer";
            case "admin" -> "admin";
            default -> role.trim().toLowerCase(Locale.ROOT);
        };
    }

    private User findUser(String id) {
        UUID userId;
        try {
            userId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "ID người dùng không hợp lệ");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy người dùng"));
    }

    private AdminUserSummaryResponse mapAdminUserSummary(ResultSet rs, int rowNumber) throws SQLException {
        return new AdminUserSummaryResponse(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("full_name"),
                rs.getString("phone"),
                toFrontendUserRole(rs.getString("role")),
                toFrontendUserStatus(rs.getString("status")),
                rs.getTimestamp("email_verified_at") != null,
                toLocalDateTime(rs, "last_login_at"),
                toLocalDateTime(rs, "created_at"),
                toLocalDateTime(rs, "updated_at")
        );
    }

    private AdminUserSummaryResponse toAdminUserSummary(User user) {
        return new AdminUserSummaryResponse(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRoleEnum() == null ? toFrontendUserRole(user.getRole()) : user.getRoleEnum().name(),
                user.getStatusEnum() == null ? toFrontendUserStatus(user.getStatus()) : user.getStatusEnum().name(),
                user.isEmailVerified(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private String toFrontendUserRole(String role) {
        if (role == null) {
            return "CANDIDATE";
        }
        return switch (role.trim().toLowerCase(Locale.ROOT)) {
            case "job_seeker", "candidate" -> "CANDIDATE";
            case "employer" -> "EMPLOYER";
            case "admin" -> "ADMIN";
            default -> role.trim().toUpperCase(Locale.ROOT);
        };
    }

    private String toFrontendUserStatus(String status) {
        if (status == null) {
            return "PENDING_VERIFICATION";
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "active" -> "ACTIVE";
            case "suspended" -> "SUSPENDED";
            default -> "PENDING_VERIFICATION";
        };
    }

    @Transactional(readOnly = true)
    public List<AdminCompanySummaryResponse> listCompanies(String status) {
        requireAdmin();
        List<Company> companies = resolveCompanies(status);
        return companies.stream().map(this::toSummaryResponse).toList();
    }

    @Transactional(readOnly = true)
    public AdminCompanyDetailResponse getCompanyDetail(String id) {
        requireAdmin();
        Company company = findCompany(id);
        return toDetailResponse(company);
    }

    @Transactional
    public AdminCompanyDetailResponse approveCompany(String id) {
        User admin = requireAdminUser();
        Company company = findCompany(id);
        if ("verified".equalsIgnoreCase(company.getVerificationStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALREADY_APPROVED", "Hồ sơ công ty đã được duyệt trước đó");
        }

        LocalDateTime now = LocalDateTime.now();
        company.setVerificationStatus("verified");
        company.setStatus("active");
        companyRepository.save(company);

        reviewPendingDocuments(company, admin, "approved", null, now);
        updateEmployerVerification(company.getId(), "verified");
        adminOpsService.writeAudit(admin.getId().toString(), "COMPANY_APPROVE", "company", id, "pending", "verified");

        return toDetailResponse(company);
    }

    @Transactional
    public AdminCompanyDetailResponse rejectCompany(String id, CompanyReviewRequest request) {
        User admin = requireAdminUser();
        if (request == null || !StringUtils.hasText(request.reason())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Vui lòng nhập lý do từ chối");
        }

        Company company = findCompany(id);
        LocalDateTime now = LocalDateTime.now();
        String reason = request.reason().trim();

        company.setVerificationStatus("rejected");
        company.setStatus("rejected");
        companyRepository.save(company);

        reviewPendingDocuments(company, admin, "rejected", reason, now);
        updateEmployerVerification(company.getId(), "rejected");
        adminOpsService.writeAudit(admin.getId().toString(), "COMPANY_REJECT", "company", id, "pending", "rejected");

        return toDetailResponse(company);
    }

    private List<Company> resolveCompanies(String status) {
        if (!StringUtils.hasText(status) || "all".equalsIgnoreCase(status)) {
            return companyRepository.findAllByOrderByUpdatedAtDesc();
        }
        return companyRepository.findByVerificationStatusIgnoreCaseOrderByUpdatedAtDesc(status.trim().toLowerCase(Locale.ROOT));
    }

    private Company findCompany(String id) {
        UUID companyId;
        try {
            companyId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "ID công ty không hợp lệ");
        }
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy công ty"));
    }

    private void requireAdmin() {
        UserResponse user = authService.getCurrentUserResponse();
        if (!"ADMIN".equalsIgnoreCase(user.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ dành cho quản trị viên");
        }
    }

    private User requireAdminUser() {
        requireAdmin();
        UserResponse current = authService.getCurrentUserResponse();
        UUID userId;
        try {
            userId = UUID.fromString(current.id());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Không tìm thấy người dùng quản trị");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Không tìm thấy người dùng quản trị"));
    }

    private void reviewPendingDocuments(Company company, User admin, String status, String reason, LocalDateTime reviewedAt) {
        List<CompanyDocument> pendingDocs = companyDocumentRepository
                .findByCompanyIdAndStatusIgnoreCase(company.getId(), "pending");
        for (CompanyDocument doc : pendingDocs) {
            doc.setStatus(status);
            doc.setRejectReason("rejected".equalsIgnoreCase(status) ? reason : null);
            doc.setReviewedAt(reviewedAt);
            doc.setReviewedBy(admin);
            companyDocumentRepository.save(doc);
        }
    }

    private void updateEmployerVerification(UUID companyId, String status) {
        employerRepository.findOwnerByCompanyId(companyId)
                .ifPresent(owner -> {
                    owner.setVerificationStatus(status);
                    employerRepository.save(owner);
                });
    }

    private AdminCompanySummaryResponse toSummaryResponse(Company company) {
        Employer owner = findPrimaryEmployer(company.getId());

        String ownerEmail = owner != null && owner.getUser() != null ? owner.getUser().getEmail() : null;
        String ownerName = owner != null && owner.getUser() != null ? owner.getUser().getFullName() : null;
        long documentCount = companyDocumentRepository.countByCompanyId(company.getId());
        long pendingDocumentCount = companyDocumentRepository.countByCompanyIdAndStatusIgnoreCase(company.getId(), "pending");

        return new AdminCompanySummaryResponse(
                String.valueOf(company.getId()),
                company.getName(),
                company.getIndustry(),
                company.getTaxCode(),
                company.getVerificationStatus(),
                company.getStatus(),
                ownerEmail,
                ownerName,
                (int) documentCount,
                (int) pendingDocumentCount,
                company.getCreatedAt(),
                company.getUpdatedAt()
        );
    }

    private AdminCompanyDetailResponse toDetailResponse(Company company) {
        List<CompanyLocationResponse> locations = companyLocationRepository
                .findByCompanyIdOrderByHeadquarterDescCreatedAtDesc(company.getId())
                .stream()
                .map(dtoMapper::toCompanyLocationResponse)
                .toList();

        List<CompanyIndustryResponse> industries = companyIndustryRepository
                .findByCompanyIdOrderByPrimaryDescCreatedAtDesc(company.getId())
                .stream()
                .map(dtoMapper::toCompanyIndustryResponse)
                .toList();

        CompanyProfileResponse profile = new CompanyProfileResponse(
                String.valueOf(company.getId()),
                company.getName(),
                company.getDescription(),
                company.getWebsite(),
                company.getIndustry(),
                company.getLocation(),
                company.getCompanySize(),
                company.getTaxCode(),
                company.getLogoUrl(),
                company.isVerified(),
                company.getVerificationStatus(),
                company.getStatus(),
                locations,
                industries
        );

        List<CompanyDocumentResponse> documents = companyDocumentRepository
                .findByCompanyIdOrderByUploadedAtDesc(company.getId())
                .stream()
                .map(dtoMapper::toCompanyDocumentResponse)
                .toList();

        AdminCompanyOwnerResponse ownerResponse = buildOwnerResponse(company.getId());

        return new AdminCompanyDetailResponse(
                profile,
                documents,
                ownerResponse,
                company.getCreatedAt(),
                company.getUpdatedAt()
        );
    }

    private AdminCompanyOwnerResponse buildOwnerResponse(UUID companyId) {
        Employer owner = findPrimaryEmployer(companyId);
        if (owner == null || owner.getUser() == null) {
            return null;
        }
        User user = owner.getUser();
        return new AdminCompanyOwnerResponse(
                String.valueOf(owner.getId()),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                owner.getPosition(),
                owner.getVerificationStatus()
        );
    }

    private Employer findPrimaryEmployer(UUID companyId) {
        return employerRepository.findOwnerByCompanyId(companyId)
                .orElseGet(() -> employerRepository.findByCompanyIdWithUser(companyId).stream().findFirst().orElse(null));
    }

    @Transactional(readOnly = true)
    public List<AdminJobSummaryResponse> listJobs(String status) {
        requireAdmin();
        String sql = """
                SELECT
                    j.id::text AS id,
                    j.title,
                    j.status,
                    j.location,
                    j.salary_min,
                    j.salary_max,
                    j.created_at,
                    j.updated_at,
                    c.name AS company_name,
                    u.email AS employer_email,
                    u.full_name AS employer_name
                FROM jobs j
                JOIN companies c ON c.id = j.company_id
                JOIN employers e ON e.id = j.created_by_employer_id
                JOIN users u ON u.id = e.user_id
                WHERE LOWER(j.status) = :status
                ORDER BY j.updated_at DESC NULLS LAST, j.created_at DESC
                """;
        return namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource("status", normalizeJobStatus(status)),
                this::mapJobSummary
        );
    }

    @Transactional(readOnly = true)
    public AdminJobDetailResponse getJobDetail(String id) {
        requireAdmin();
        return findJobDetail(id);
    }

    @Transactional
    public AdminJobDetailResponse approveJob(String id) {
        User admin = requireAdminUser();
        ensurePendingJob(id);
        String sql = """
                UPDATE jobs
                SET status = 'published',
                    published_at = COALESCE(published_at, now()),
                    posted_at = COALESCE(posted_at, now()),
                    rejection_reason = NULL,
                    reviewed_by_user_id = CAST(:adminId AS uuid),
                    updated_at = now()
                WHERE id = CAST(:id AS uuid)
                """;
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("adminId", admin.getId().toString())
        );
        adminOpsService.writeAudit(admin.getId().toString(), "JOB_APPROVE", "job", id, "pending_review", "published");
        return findJobDetail(id);
    }

    @Transactional
    public AdminJobDetailResponse rejectJob(String id, CompanyReviewRequest request) {
        User admin = requireAdminUser();
        if (request == null || !StringUtils.hasText(request.reason())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Vui lòng nhập lý do từ chối");
        }
        ensurePendingJob(id);
        String sql = """
                UPDATE jobs
                SET status = 'rejected',
                    rejection_reason = :reason,
                    reviewed_by_user_id = CAST(:adminId AS uuid),
                    updated_at = now()
                WHERE id = CAST(:id AS uuid)
                """;
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("reason", request.reason().trim())
                        .addValue("adminId", admin.getId().toString())
        );
        adminOpsService.writeAudit(admin.getId().toString(), "JOB_REJECT", "job", id, "pending_review", "rejected");
        return findJobDetail(id);
    }

    @Transactional
    public AdminJobDetailResponse closeJob(String id, CompanyReviewRequest request) {
        User admin = requireAdminUser();
        AdminJobDetailResponse current = findJobDetail(id);
        String normalized = toDbJobStatus(current.job().status());
        if (!"published".equals(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Chỉ có thể ẩn tin đang được công khai");
        }
        String reason = request != null && StringUtils.hasText(request.reason())
                ? request.reason().trim()
                : "Admin ẩn tin tuyển dụng";
        namedParameterJdbcTemplate.update("""
                UPDATE jobs
                SET status = 'closed',
                    closed_at = now(),
                    rejection_reason = :reason,
                    reviewed_by_user_id = CAST(:adminId AS uuid),
                    updated_at = now()
                WHERE id = CAST(:id AS uuid)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("reason", reason)
                        .addValue("adminId", admin.getId().toString())
        );
        adminOpsService.writeAudit(admin.getId().toString(), "JOB_CLOSE", "job", id, "published", "closed");
        return findJobDetail(id);
    }

    @Transactional
    public AdminJobDetailResponse reopenJob(String id) {
        User admin = requireAdminUser();
        AdminJobDetailResponse current = findJobDetail(id);
        String normalized = toDbJobStatus(current.job().status());
        if (!"closed".equals(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Chỉ có thể mở lại tin đã bị ẩn");
        }
        namedParameterJdbcTemplate.update("""
                UPDATE jobs
                SET status = 'published',
                    closed_at = NULL,
                    rejection_reason = NULL,
                    reviewed_by_user_id = CAST(:adminId AS uuid),
                    updated_at = now()
                WHERE id = CAST(:id AS uuid)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("adminId", admin.getId().toString())
        );
        adminOpsService.writeAudit(admin.getId().toString(), "JOB_REOPEN", "job", id, "closed", "published");
        return findJobDetail(id);
    }

    private String normalizeJobStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "pending_review";
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "pending", "pending_review" -> "pending_review";
            case "published", "active", "approved" -> "published";
            case "rejected" -> "rejected";
            case "closed", "hidden", "violations", "violating" -> "closed";
            default -> status.trim().toLowerCase(Locale.ROOT);
        };
    }

    private String toDbJobStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "";
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "published", "active" -> "published";
            case "pending_review", "pending" -> "pending_review";
            case "rejected" -> "rejected";
            case "closed", "hidden", "violations", "violating" -> "closed";
            case "expired" -> "expired";
            case "draft" -> "draft";
            default -> status.trim().toLowerCase(Locale.ROOT);
        };
    }

    private void ensurePendingJob(String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "ID việc làm không hợp lệ");
        }
        String sql = "SELECT status FROM jobs WHERE id = CAST(:id AS uuid)";
        List<String> statuses = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource("id", id),
                (rs, rowNum) -> rs.getString("status")
        );
        if (statuses.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy việc làm");
        }
        if (!"pending_review".equalsIgnoreCase(statuses.get(0))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Chỉ có thể thao tác với tin đang chờ duyệt");
        }
    }

    private AdminJobSummaryResponse mapJobSummary(ResultSet rs, int rowNumber) throws SQLException {
        return new AdminJobSummaryResponse(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("company_name"),
                rs.getString("employer_email"),
                rs.getString("employer_name"),
                rs.getString("status"),
                rs.getString("location"),
                rs.getBigDecimal("salary_min"),
                rs.getBigDecimal("salary_max"),
                toLocalDateTime(rs, "created_at"),
                toLocalDateTime(rs, "updated_at")
        );
    }

    private AdminJobDetailResponse findJobDetail(String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "ID việc làm không hợp lệ");
        }
        String sql = """
                SELECT
                    j.id::text AS id,
                    j.title,
                    j.description,
                    j.requirements,
                    j.location,
                    j.experience_level,
                    j.deadline,
                    j.status,
                    j.salary_min,
                    j.salary_max,
                    j.rejection_reason,
                    j.views_count,
                    j.created_at,
                    j.updated_at,
                    c.id::text AS company_id,
                    c.name AS company_name,
                    c.website AS company_website,
                    c.location AS company_location,
                    c.logo_url AS company_logo_url,
                    u.email AS employer_email,
                    u.full_name AS employer_name,
                    e.position AS employer_position
                FROM jobs j
                JOIN companies c ON c.id = j.company_id
                JOIN employers e ON e.id = j.created_by_employer_id
                JOIN users u ON u.id = e.user_id
                WHERE j.id = CAST(:id AS uuid)
                """;
        List<AdminJobDetailResponse> jobs = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource("id", id),
                this::mapJobDetail
        );
        return jobs.stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy việc làm"));
    }

    private AdminJobDetailResponse mapJobDetail(ResultSet rs, int rowNumber) throws SQLException {
        JobResponse job = new JobResponse(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("description"),
                textToList(rs.getString("requirements")),
                List.<String>of(),
                rs.getBigDecimal("salary_min"),
                rs.getBigDecimal("salary_max"),
                rs.getString("location"),
                rs.getString("experience_level"),
                readDeadline(rs),
                toFrontendJobStatus(rs.getString("status")),
                new CompanyResponse(
                        rs.getString("company_id"),
                        rs.getString("company_name"),
                        rs.getString("company_website"),
                        rs.getString("company_location"),
                        rs.getString("company_logo_url")
                ),
                null,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                rs.getInt("views_count"),
                rs.getString("rejection_reason"),
                0L
        );
        return new AdminJobDetailResponse(
                job,
                rs.getString("employer_email"),
                rs.getString("employer_name"),
                rs.getString("employer_position"),
                toLocalDateTime(rs, "created_at"),
                toLocalDateTime(rs, "updated_at")
        );
    }

    private List<String> textToList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private LocalDateTime readDeadline(ResultSet rs) throws SQLException {
        java.sql.Date deadline = rs.getDate("deadline");
        return deadline == null ? null : deadline.toLocalDate().atStartOfDay();
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String toFrontendJobStatus(String status) {
        if (status == null) {
            return "DRAFT";
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "published", "active" -> "PUBLISHED";
            case "pending_review" -> "PENDING_REVIEW";
            case "rejected" -> "REJECTED";
            case "closed" -> "CLOSED";
            case "expired" -> "EXPIRED";
            default -> status.toUpperCase(Locale.ROOT);
        };
    }
}
