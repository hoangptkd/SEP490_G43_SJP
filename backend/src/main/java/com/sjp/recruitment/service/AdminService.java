package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.CompanyReviewRequest;
import com.sjp.recruitment.model.dto.response.AdminCompanyDetailResponse;
import com.sjp.recruitment.model.dto.response.AdminCompanyOwnerResponse;
import com.sjp.recruitment.model.dto.response.AdminCompanySummaryResponse;
import com.sjp.recruitment.model.dto.response.AdminDashboardResponse;
import com.sjp.recruitment.model.dto.response.AdminJobDetailResponse;
import com.sjp.recruitment.model.dto.response.AdminJobSummaryResponse;
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
import com.sjp.recruitment.repository.CompanyDocumentRepository;
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
import java.time.LocalDateTime;
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
    private final EmployerRepository employerRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final DtoMapper dtoMapper;

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
                LocalDateTime.now()
        );
    }

    private long count(String sql) {
        Long value = namedParameterJdbcTemplate.getJdbcTemplate().queryForObject(sql, Long.class);
        return value == null ? 0 : value;
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
                locations
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
        requireAdmin();
        ensurePendingJob(id);
        String sql = """
                UPDATE jobs
                SET status = 'published',
                    published_at = COALESCE(published_at, now()),
                    posted_at = COALESCE(posted_at, now()),
                    rejection_reason = NULL,
                    updated_at = now()
                WHERE id = CAST(:id AS uuid)
                """;
        namedParameterJdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
        return findJobDetail(id);
    }

    @Transactional
    public AdminJobDetailResponse rejectJob(String id, CompanyReviewRequest request) {
        requireAdmin();
        if (request == null || !StringUtils.hasText(request.reason())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Vui lòng nhập lý do từ chối");
        }
        ensurePendingJob(id);
        String sql = """
                UPDATE jobs
                SET status = 'rejected',
                    rejection_reason = :reason,
                    updated_at = now()
                WHERE id = CAST(:id AS uuid)
                """;
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("reason", request.reason().trim())
        );
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
