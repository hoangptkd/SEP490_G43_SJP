package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.JobReportRequest;
import com.sjp.recruitment.model.dto.request.JobReportResolveRequest;
import com.sjp.recruitment.model.dto.response.AdminJobReportResponse;
import com.sjp.recruitment.model.dto.response.JobReportResponse;
import com.sjp.recruitment.model.dto.response.UserResponse;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobReportService {

    private static final Set<String> ALLOWED_REASONS = Set.of(
            "spam",
            "scam",
            "offensive",
            "misleading",
            "discrimination",
            "other"
    );

    private final AuthService authService;
    private final UserRepository userRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final AdminOpsService adminOpsService;

    @Transactional
    public JobReportResponse reportJob(String jobId, JobReportRequest request) {
        User reporter = requireCandidateUser();
        ensureUuid(jobId, "Việc làm");

        if (request == null || !StringUtils.hasText(request.reason())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Vui lòng chọn lý do báo cáo");
        }

        String reason = request.reason().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_REASONS.contains(reason)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REASON", "Lý do báo cáo không hợp lệ");
        }
        String description = request.description() != null ? request.description().trim() : null;

        String jobStatus = namedParameterJdbcTemplate.query(
                "SELECT status FROM jobs WHERE id = CAST(:id AS uuid)",
                new MapSqlParameterSource("id", jobId),
                rs -> rs.next() ? rs.getString(1) : null
        );
        if (!StringUtils.hasText(jobStatus)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy việc làm");
        }
        if (!"published".equalsIgnoreCase(jobStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "JOB_NOT_PUBLIC", "Chỉ có thể báo cáo tin đang công khai");
        }

        Long existing = namedParameterJdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM job_reports
                WHERE job_id = CAST(:jobId AS uuid)
                  AND reporter_user_id = CAST(:userId AS uuid)
                  AND status = 'pending'
                """,
                new MapSqlParameterSource()
                        .addValue("jobId", jobId)
                        .addValue("userId", reporter.getId().toString()),
                Long.class
        );
        if (existing != null && existing > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REPORTED", "Bạn đã gửi báo cáo cho tin này và đang chờ xử lý");
        }

        ReporterSnapshot reporterSnapshot = loadReporterSnapshot(reporter.getId().toString());
        if (!StringUtils.hasText(reporterSnapshot.name()) || !StringUtils.hasText(reporterSnapshot.phone())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROFILE_INCOMPLETE",
                    "Vui lòng cập nhật họ tên và số điện thoại trong hồ sơ trước khi gửi báo cáo"
            );
        }

        String id = namedParameterJdbcTemplate.query("""
                INSERT INTO job_reports (
                    job_id, reporter_user_id, reason, description, status,
                    reporter_full_name, reporter_phone, reporter_date_of_birth
                )
                VALUES (
                    CAST(:jobId AS uuid), CAST(:userId AS uuid), :reason, :description, 'pending',
                    :reporterName, :reporterPhone, :reporterDob
                )
                RETURNING id::text
                """,
                new MapSqlParameterSource()
                        .addValue("jobId", jobId)
                        .addValue("userId", reporter.getId().toString())
                        .addValue("reason", reason)
                        .addValue("description", description)
                        .addValue("reporterName", reporterSnapshot.name())
                        .addValue("reporterPhone", reporterSnapshot.phone())
                        .addValue("reporterDob", reporterSnapshot.dateOfBirth() != null
                                ? java.sql.Date.valueOf(reporterSnapshot.dateOfBirth())
                                : null),
                rs -> rs.next() ? rs.getString(1) : null
        );

        if (!StringUtils.hasText(id)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CREATE_FAILED", "Không gửi được báo cáo");
        }

        return findReportForUser(id, reporter.getId().toString());
    }

    @Transactional(readOnly = true)
    public List<AdminJobReportResponse> listReports(String status) {
        requireAdmin();
        StringBuilder sql = new StringBuilder("""
                SELECT r.id::text AS id,
                       r.job_id::text AS job_id,
                       j.title AS job_title,
                       c.name AS company_name,
                       j.status AS job_status,
                       r.reporter_user_id::text AS reporter_user_id,
                       u.email AS reporter_email,
                       COALESCE(r.reporter_full_name, u.full_name) AS reporter_name,
                       COALESCE(r.reporter_phone, u.phone) AS reporter_phone,
                       COALESCE(r.reporter_date_of_birth, js.date_of_birth) AS reporter_date_of_birth,
                       r.reason,
                       r.description,
                       r.status,
                       r.admin_note,
                       r.created_at,
                       r.resolved_at,
                       r.company_fix_deadline
                FROM job_reports r
                JOIN jobs j ON j.id = r.job_id
                JOIN companies c ON c.id = j.company_id
                JOIN users u ON u.id = r.reporter_user_id
                LEFT JOIN job_seekers js ON js.user_id = u.id
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status)) {
            sql.append(" WHERE LOWER(r.status) = :status");
            params.addValue("status", status.trim().toLowerCase(Locale.ROOT));
        }
        sql.append(" ORDER BY r.created_at DESC");
        return namedParameterJdbcTemplate.query(sql.toString(), params, this::mapAdminReport);
    }

    @Transactional
    public AdminJobReportResponse notifyCompany(String reportId, JobReportResolveRequest request) {
        User admin = requireAdminUser();
        AdminJobReportResponse current = findAdminReport(reportId);
        if (!"pending".equalsIgnoreCase(current.status()) && !"awaiting_company".equalsIgnoreCase(current.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALREADY_HANDLED", "Báo cáo này đã được xử lý");
        }

        String note = request != null && StringUtils.hasText(request.adminNote())
                ? request.adminNote().trim()
                : null;
        if (!StringUtils.hasText(note)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOTE_REQUIRED", "Vui lòng nhập lý do thông báo cho công ty");
        }

        LocalDateTime fixDeadline = LocalDateTime.now().plusDays(3);

        namedParameterJdbcTemplate.update("""
                UPDATE jobs
                SET status = 'awaiting_company',
                    closed_at = now(),
                    rejection_reason = :note,
                    report_fix_deadline = :fixDeadline,
                    reviewed_by_user_id = CAST(:adminId AS uuid),
                    updated_at = now()
                WHERE id = CAST(:jobId AS uuid)
                """,
                new MapSqlParameterSource()
                        .addValue("jobId", current.jobId())
                        .addValue("note", note)
                        .addValue("fixDeadline", fixDeadline)
                        .addValue("adminId", admin.getId().toString())
        );

        namedParameterJdbcTemplate.update("""
                UPDATE job_reports
                SET status = 'awaiting_company',
                    admin_note = :note,
                    company_fix_deadline = :fixDeadline,
                    resolved_by = CAST(:adminId AS uuid),
                    resolved_at = now()
                WHERE job_id = CAST(:jobId AS uuid)
                  AND status IN ('pending', 'awaiting_company')
                """,
                new MapSqlParameterSource()
                        .addValue("jobId", current.jobId())
                        .addValue("note", note)
                        .addValue("fixDeadline", fixDeadline)
                        .addValue("adminId", admin.getId().toString())
        );

        adminOpsService.writeAudit(
                admin.getId().toString(),
                "JOB_REPORT_NOTIFY_COMPANY",
                "job",
                current.jobId(),
                current.jobStatus(),
                "awaiting_company"
        );
        return findAdminReport(reportId);
    }

    @Transactional
    public AdminJobReportResponse dismissReport(String reportId, JobReportResolveRequest request) {
        User admin = requireAdminUser();
        AdminJobReportResponse current = findAdminReport(reportId);
        if (!"pending".equalsIgnoreCase(current.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALREADY_HANDLED", "Báo cáo này đã được xử lý");
        }

        String note = request != null && StringUtils.hasText(request.adminNote())
                ? request.adminNote().trim()
                : "Không vi phạm — tin tiếp tục hoạt động";

        namedParameterJdbcTemplate.update("""
                UPDATE job_reports
                SET status = 'dismissed',
                    admin_note = :note,
                    resolved_by = CAST(:adminId AS uuid),
                    resolved_at = now()
                WHERE id = CAST(:id AS uuid)
                """,
                new MapSqlParameterSource()
                        .addValue("id", reportId)
                        .addValue("note", note)
                        .addValue("adminId", admin.getId().toString())
        );

        adminOpsService.writeAudit(
                admin.getId().toString(),
                "JOB_REPORT_DISMISS",
                "job_report",
                reportId,
                "pending",
                "dismissed"
        );
        return findAdminReport(reportId);
    }

    @Transactional
    public AdminJobReportResponse resolveReport(String reportId, JobReportResolveRequest request) {
        User admin = requireAdminUser();
        AdminJobReportResponse current = findAdminReport(reportId);
        if (!"pending".equalsIgnoreCase(current.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALREADY_HANDLED", "Báo cáo này đã được xử lý");
        }

        String note = request != null && StringUtils.hasText(request.adminNote())
                ? request.adminNote().trim()
                : "Xác nhận vi phạm — đã gỡ tin";

        namedParameterJdbcTemplate.update("""
                UPDATE jobs
                SET status = 'removed',
                    closed_at = now(),
                    rejection_reason = :note,
                    report_fix_deadline = NULL,
                    reviewed_by_user_id = CAST(:adminId AS uuid),
                    updated_at = now()
                WHERE id = CAST(:jobId AS uuid)
                """,
                new MapSqlParameterSource()
                        .addValue("jobId", current.jobId())
                        .addValue("note", note)
                        .addValue("adminId", admin.getId().toString())
        );

        namedParameterJdbcTemplate.update("""
                UPDATE job_reports
                SET status = 'resolved',
                    admin_note = :note,
                    company_fix_deadline = NULL,
                    resolved_by = CAST(:adminId AS uuid),
                    resolved_at = now()
                WHERE id = CAST(:id AS uuid)
                """,
                new MapSqlParameterSource()
                        .addValue("id", reportId)
                        .addValue("note", note)
                        .addValue("adminId", admin.getId().toString())
        );

        // Đóng các báo cáo pending khác cùng tin
        namedParameterJdbcTemplate.update("""
                UPDATE job_reports
                SET status = 'resolved',
                    admin_note = COALESCE(admin_note, :note),
                    company_fix_deadline = NULL,
                    resolved_by = CAST(:adminId AS uuid),
                    resolved_at = now()
                WHERE job_id = CAST(:jobId AS uuid)
                  AND status IN ('pending', 'awaiting_company')
                  AND id <> CAST(:id AS uuid)
                """,
                new MapSqlParameterSource()
                        .addValue("jobId", current.jobId())
                        .addValue("id", reportId)
                        .addValue("note", note)
                        .addValue("adminId", admin.getId().toString())
        );

        adminOpsService.writeAudit(
                admin.getId().toString(),
                "JOB_REPORT_RESOLVE",
                "job",
                current.jobId(),
                current.jobStatus(),
                "removed"
        );
        return findAdminReport(reportId);
    }

    private JobReportResponse findReportForUser(String id, String userId) {
        List<JobReportResponse> rows = namedParameterJdbcTemplate.query("""
                SELECT id::text AS id, job_id::text AS job_id, reason, description, status, created_at
                FROM job_reports
                WHERE id = CAST(:id AS uuid) AND reporter_user_id = CAST(:userId AS uuid)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userId", userId),
                (rs, rowNum) -> new JobReportResponse(
                        rs.getString("id"),
                        rs.getString("job_id"),
                        rs.getString("reason"),
                        rs.getString("description"),
                        rs.getString("status"),
                        toLocalDateTime(rs, "created_at")
                )
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy báo cáo");
        }
        return rows.get(0);
    }

    private AdminJobReportResponse findAdminReport(String id) {
        ensureUuid(id, "Báo cáo");
        List<AdminJobReportResponse> rows = namedParameterJdbcTemplate.query("""
                SELECT r.id::text AS id,
                       r.job_id::text AS job_id,
                       j.title AS job_title,
                       c.name AS company_name,
                       j.status AS job_status,
                       r.reporter_user_id::text AS reporter_user_id,
                       u.email AS reporter_email,
                       COALESCE(r.reporter_full_name, u.full_name) AS reporter_name,
                       COALESCE(r.reporter_phone, u.phone) AS reporter_phone,
                       COALESCE(r.reporter_date_of_birth, js.date_of_birth) AS reporter_date_of_birth,
                       r.reason,
                       r.description,
                       r.status,
                       r.admin_note,
                       r.created_at,
                       r.resolved_at,
                       r.company_fix_deadline
                FROM job_reports r
                JOIN jobs j ON j.id = r.job_id
                JOIN companies c ON c.id = j.company_id
                JOIN users u ON u.id = r.reporter_user_id
                LEFT JOIN job_seekers js ON js.user_id = u.id
                WHERE r.id = CAST(:id AS uuid)
                """,
                new MapSqlParameterSource("id", id),
                this::mapAdminReport
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy báo cáo");
        }
        return rows.get(0);
    }

    private AdminJobReportResponse mapAdminReport(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Date dobSql = rs.getDate("reporter_date_of_birth");
        LocalDate dob = dobSql == null ? null : dobSql.toLocalDate();
        Integer age = dob == null ? null : java.time.Period.between(dob, java.time.LocalDate.now()).getYears();
        return new AdminJobReportResponse(
                rs.getString("id"),
                rs.getString("job_id"),
                rs.getString("job_title"),
                rs.getString("company_name"),
                rs.getString("job_status"),
                rs.getString("reporter_user_id"),
                rs.getString("reporter_email"),
                rs.getString("reporter_name"),
                rs.getString("reporter_phone"),
                dob,
                age,
                rs.getString("reason"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getString("admin_note"),
                toLocalDateTime(rs, "created_at"),
                toLocalDateTime(rs, "resolved_at"),
                toLocalDateTime(rs, "company_fix_deadline")
        );
    }

    private record ReporterSnapshot(String name, String phone, java.time.LocalDate dateOfBirth) {}

    private ReporterSnapshot loadReporterSnapshot(String userId) {
        List<ReporterSnapshot> rows = namedParameterJdbcTemplate.query("""
                SELECT u.full_name AS full_name,
                       u.phone AS phone,
                       js.date_of_birth AS date_of_birth
                FROM users u
                LEFT JOIN job_seekers js ON js.user_id = u.id
                WHERE u.id = CAST(:userId AS uuid)
                """,
                new MapSqlParameterSource("userId", userId),
                (rs, rowNum) -> {
                    java.sql.Date dob = rs.getDate("date_of_birth");
                    return new ReporterSnapshot(
                            rs.getString("full_name"),
                            rs.getString("phone"),
                            dob == null ? null : dob.toLocalDate()
                    );
                }
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy thông tin ứng viên");
        }
        return rows.get(0);
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private void ensureUuid(String id, String label) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", label + " không hợp lệ");
        }
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
        return userRepository.findById(UUID.fromString(current.id()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Không tìm thấy người dùng quản trị"));
    }

    private User requireCandidateUser() {
        UserResponse current = authService.getCurrentUserResponse();
        String role = current.role() == null ? "" : current.role().toUpperCase(Locale.ROOT);
        if (!"CANDIDATE".equals(role) && !"JOB_SEEKER".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ ứng viên mới có thể báo cáo tin tuyển dụng");
        }
        return userRepository.findById(UUID.fromString(current.id()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Không tìm thấy người dùng"));
    }
}
