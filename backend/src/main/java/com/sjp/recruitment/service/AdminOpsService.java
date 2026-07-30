package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.AdminCategoryRequest;
import com.sjp.recruitment.model.dto.request.AdminPlanUpdateRequest;
import com.sjp.recruitment.model.dto.request.AdminSettingsUpdateRequest;
import com.sjp.recruitment.model.dto.request.SubscriptionActionRequest;
import com.sjp.recruitment.model.dto.response.AdminAuditLogResponse;
import com.sjp.recruitment.model.dto.response.AdminCategoryResponse;
import com.sjp.recruitment.model.dto.response.AdminPaymentResponse;
import com.sjp.recruitment.model.dto.response.AdminPlanResponse;
import com.sjp.recruitment.model.dto.response.AdminRevenueSummaryResponse;
import com.sjp.recruitment.model.dto.response.AdminSettingResponse;
import com.sjp.recruitment.model.dto.response.AdminSubscriptionResponse;
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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminOpsService {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final BillingService billingService;
    private final SystemSettingsService systemSettingsService;

    @Transactional(readOnly = true)
    public List<AdminPlanResponse> listPlans(String status) {
        requireAdmin();
        StringBuilder sql = new StringBuilder("""
                SELECT id::text AS id, name, target_role, description, price, currency, duration_days,
                       COALESCE(features::text, '{}') AS features_json, status, sort_order, created_at, updated_at
                FROM plans
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status)) {
            sql.append(" WHERE LOWER(status) = :status");
            params.addValue("status", status.trim().toLowerCase(Locale.ROOT));
        }
        sql.append(" ORDER BY sort_order ASC, price ASC, name ASC");
        return namedParameterJdbcTemplate.query(sql.toString(), params, this::mapPlan);
    }

    @Transactional
    public AdminPlanResponse updatePlan(String id, AdminPlanUpdateRequest request) {
        User admin = requireAdminUser();
        ensureUuid(id, "Gói dịch vụ");
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Thiếu dữ liệu cập nhật gói");
        }

        AdminPlanResponse current = findPlan(id);
        String name = StringUtils.hasText(request.name()) ? request.name().trim() : current.name();
        String targetRole = StringUtils.hasText(request.targetRole())
                ? request.targetRole().trim().toLowerCase(Locale.ROOT)
                : current.targetRole();
        String description = request.description() != null ? request.description().trim() : current.description();
        BigDecimal price = request.price() != null ? request.price() : current.price();
        String currency = StringUtils.hasText(request.currency()) ? request.currency().trim().toUpperCase(Locale.ROOT) : current.currency();
        Integer durationDays = request.durationDays() != null ? request.durationDays() : current.durationDays();
        String featuresJson = StringUtils.hasText(request.featuresJson()) ? request.featuresJson().trim() : current.featuresJson();
        String status = StringUtils.hasText(request.status())
                ? request.status().trim().toLowerCase(Locale.ROOT)
                : current.status();
        Integer sortOrder = request.sortOrder() != null ? request.sortOrder() : current.sortOrder();

        if (!List.of("job_seeker", "employer", "all").contains(targetRole)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TARGET_ROLE", "Đối tượng gói không hợp lệ");
        }
        if (!List.of("active", "inactive").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Trạng thái gói không hợp lệ");
        }
        if (price.compareTo(BigDecimal.ZERO) < 0 || durationDays == null || durationDays <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLAN", "Giá hoặc thời hạn gói không hợp lệ");
        }

        namedParameterJdbcTemplate.update("""
                UPDATE plans
                SET name = :name,
                    target_role = :targetRole,
                    description = :description,
                    price = :price,
                    currency = :currency,
                    duration_days = :durationDays,
                    features = CAST(:featuresJson AS jsonb),
                    status = :status,
                    sort_order = :sortOrder,
                    updated_at = now()
                WHERE id = CAST(:id AS uuid)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("name", name)
                        .addValue("targetRole", targetRole)
                        .addValue("description", description)
                        .addValue("price", price)
                        .addValue("currency", currency)
                        .addValue("durationDays", durationDays)
                        .addValue("featuresJson", StringUtils.hasText(featuresJson) ? featuresJson : "{}")
                        .addValue("status", status)
                        .addValue("sortOrder", sortOrder)
        );

        writeAudit(admin.getId().toString(), "PLAN_UPDATE", "plan", id, current.status(), status);
        return findPlan(id);
    }

    @Transactional
    public AdminPlanResponse createPlan(AdminPlanUpdateRequest request) {
        User admin = requireAdminUser();
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED", "Vui lòng nhập tên gói dịch vụ");
        }

        String name = request.name().trim();
        String targetRole = StringUtils.hasText(request.targetRole())
                ? request.targetRole().trim().toLowerCase(Locale.ROOT)
                : "all";
        String description = request.description() != null ? request.description().trim() : null;
        BigDecimal price = request.price() != null ? request.price() : BigDecimal.ZERO;
        String currency = StringUtils.hasText(request.currency()) ? request.currency().trim().toUpperCase(Locale.ROOT) : "VND";
        Integer durationDays = request.durationDays() != null ? request.durationDays() : 30;
        String featuresJson = StringUtils.hasText(request.featuresJson()) ? request.featuresJson().trim() : "{}";
        String status = StringUtils.hasText(request.status())
                ? request.status().trim().toLowerCase(Locale.ROOT)
                : "active";
        Integer sortOrder = request.sortOrder() != null ? request.sortOrder() : 0;

        if (!List.of("job_seeker", "employer", "all").contains(targetRole)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TARGET_ROLE", "Đối tượng gói không hợp lệ");
        }
        if (!List.of("active", "inactive").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Trạng thái gói không hợp lệ");
        }
        if (price.compareTo(BigDecimal.ZERO) < 0 || durationDays <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLAN", "Giá hoặc thời hạn gói không hợp lệ");
        }

        Long existing = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plans WHERE LOWER(name) = LOWER(:name)",
                new MapSqlParameterSource("name", name),
                Long.class
        );
        if (existing != null && existing > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_EXISTS", "Tên gói đã tồn tại");
        }

        String id = namedParameterJdbcTemplate.query("""
                INSERT INTO plans (name, target_role, description, price, currency, duration_days, features, status, sort_order)
                VALUES (:name, :targetRole, :description, :price, :currency, :durationDays, CAST(:featuresJson AS jsonb), :status, :sortOrder)
                RETURNING id::text
                """,
                new MapSqlParameterSource()
                        .addValue("name", name)
                        .addValue("targetRole", targetRole)
                        .addValue("description", description)
                        .addValue("price", price)
                        .addValue("currency", currency)
                        .addValue("durationDays", durationDays)
                        .addValue("featuresJson", featuresJson)
                        .addValue("status", status)
                        .addValue("sortOrder", sortOrder),
                rs -> rs.next() ? rs.getString(1) : null
        );

        if (!StringUtils.hasText(id)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CREATE_FAILED", "Không tạo được gói dịch vụ");
        }

        writeAudit(admin.getId().toString(), "PLAN_CREATE", "plan", id, null, status);
        return findPlan(id);
    }

    @Transactional
    public void deletePlan(String id) {
        User admin = requireAdminUser();
        ensureUuid(id, "Gói dịch vụ");
        AdminPlanResponse current = findPlan(id);

        Long subscriptionCount = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subscriptions WHERE plan_id = CAST(:id AS uuid)",
                new MapSqlParameterSource("id", id),
                Long.class
        );
        if (subscriptionCount != null && subscriptionCount > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PLAN_IN_USE",
                    "Không thể xóa gói đang có " + subscriptionCount + " đăng ký. Hãy chuyển gói sang 'Tạm tắt'."
            );
        }

        namedParameterJdbcTemplate.update(
                "DELETE FROM plans WHERE id = CAST(:id AS uuid)",
                new MapSqlParameterSource("id", id)
        );
        writeAudit(admin.getId().toString(), "PLAN_DELETE", "plan", id, current.status(), "deleted");
    }

    @Transactional(readOnly = true)
    public List<AdminSubscriptionResponse> listSubscriptions(String status) {
        requireAdmin();
        StringBuilder sql = new StringBuilder("""
                SELECT s.id::text AS id,
                       s.user_id::text AS user_id,
                       u.email AS user_email,
                       u.full_name AS user_name,
                       s.plan_id::text AS plan_id,
                       p.name AS plan_name,
                       s.status,
                       s.start_date,
                       s.end_date,
                       s.cancelled_at,
                       s.cancelled_reason,
                       s.created_at,
                       s.updated_at
                FROM subscriptions s
                JOIN users u ON u.id = s.user_id
                JOIN plans p ON p.id = s.plan_id
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status)) {
            sql.append(" WHERE LOWER(s.status) = :status");
            params.addValue("status", status.trim().toLowerCase(Locale.ROOT));
        }
        sql.append(" ORDER BY s.updated_at DESC NULLS LAST, s.created_at DESC");
        return namedParameterJdbcTemplate.query(sql.toString(), params, this::mapSubscription);
    }

    @Transactional
    public AdminSubscriptionResponse cancelSubscription(String id, SubscriptionActionRequest request) {
        User admin = requireAdminUser();
        ensureUuid(id, "Đăng ký");
        AdminSubscriptionResponse current = findSubscription(id);
        if ("cancelled".equalsIgnoreCase(current.status())) {
            return current;
        }
        String reason = request != null && StringUtils.hasText(request.reason())
                ? request.reason().trim()
                : "Admin hủy đăng ký";
        namedParameterJdbcTemplate.update("""
                UPDATE subscriptions
                SET status = 'cancelled',
                    cancelled_at = now(),
                    cancelled_reason = :reason,
                    updated_at = now()
                WHERE id = CAST(:id AS uuid)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("reason", reason)
        );
        writeAudit(admin.getId().toString(), "SUBSCRIPTION_CANCEL", "subscription", id, current.status(), "cancelled");
        return findSubscription(id);
    }

    @Transactional
    public AdminSubscriptionResponse activateSubscription(String id) {
        User admin = requireAdminUser();
        ensureUuid(id, "Đăng ký");
        AdminSubscriptionResponse current = findSubscription(id);
        namedParameterJdbcTemplate.update("""
                UPDATE subscriptions
                SET status = 'active',
                    start_date = COALESCE(start_date, now()),
                    cancelled_at = NULL,
                    cancelled_reason = NULL,
                    updated_at = now()
                WHERE id = CAST(:id AS uuid)
                """,
                new MapSqlParameterSource("id", id)
        );
        writeAudit(admin.getId().toString(), "SUBSCRIPTION_ACTIVATE", "subscription", id, current.status(), "active");
        return findSubscription(id);
    }

    @Transactional
    public AdminPaymentResponse confirmBankPayment(String id) {
        User admin = requireAdminUser();
        ensureUuid(id, "Thanh toán");
        billingService.confirmBankTransferAsAdmin(id);
        writeAudit(admin.getId().toString(), "PAYMENT_CONFIRM", "payment", id, "pending", "paid");
        return listPayments("all").stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Không tìm thấy giao dịch"));
    }

    @Transactional(readOnly = true)
    public List<AdminPaymentResponse> listPayments(String status) {
        requireAdmin();
        StringBuilder sql = new StringBuilder("""
                SELECT p.id::text AS id,
                       p.subscription_id::text AS subscription_id,
                       p.user_id::text AS user_id,
                       u.email AS user_email,
                       pl.name AS plan_name,
                       p.amount,
                       p.currency,
                       p.payment_method,
                       p.gateway,
                       p.status,
                       p.transaction_id,
                       p.failure_reason,
                       p.gateway_order_id,
                       p.gateway_response::text AS gateway_response,
                       p.paid_at,
                       p.created_at
                FROM payments p
                JOIN users u ON u.id = p.user_id
                LEFT JOIN subscriptions s ON s.id = p.subscription_id
                LEFT JOIN plans pl ON pl.id = s.plan_id
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status)) {
            sql.append(" WHERE LOWER(p.status) = :status");
            params.addValue("status", status.trim().toLowerCase(Locale.ROOT));
        }
        sql.append(" ORDER BY p.created_at DESC");
        return namedParameterJdbcTemplate.query(sql.toString(), params, this::mapPayment);
    }

    @Transactional(readOnly = true)
    public AdminRevenueSummaryResponse getRevenueSummary() {
        requireAdmin();
        BigDecimal totalPaid = money("SELECT COALESCE(SUM(amount),0) FROM payments WHERE status = 'paid'");
        BigDecimal totalPending = money("SELECT COALESCE(SUM(amount),0) FROM payments WHERE status = 'pending'");
        BigDecimal totalRefunded = money("SELECT COALESCE(SUM(amount),0) FROM payments WHERE status = 'refunded'");
        long paidCount = count("SELECT COUNT(*) FROM payments WHERE status = 'paid'");
        long pendingCount = count("SELECT COUNT(*) FROM payments WHERE status = 'pending'");
        long failedCount = count("SELECT COUNT(*) FROM payments WHERE status = 'failed'");
        long activeSubscriptions = count("SELECT COUNT(*) FROM subscriptions WHERE status = 'active'");
        long activePlans = count("SELECT COUNT(*) FROM plans WHERE status = 'active'");
        return new AdminRevenueSummaryResponse(
                totalPaid, totalPending, totalRefunded,
                paidCount, pendingCount, failedCount,
                activeSubscriptions, activePlans,
                LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public List<AdminAuditLogResponse> listAuditLogs(String targetType, int limit) {
        requireAdmin();
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        StringBuilder sql = new StringBuilder("""
                SELECT a.id::text AS id,
                       a.actor_user_id::text AS actor_user_id,
                       u.email AS actor_email,
                       a.action,
                       a.target_type,
                       a.target_id::text AS target_id,
                       COALESCE(a.old_value_json::text, NULL) AS old_value_json,
                       COALESCE(a.new_value_json::text, NULL) AS new_value_json,
                       CAST(a.ip_address AS text) AS ip_address,
                       a.created_at
                FROM admin_audit_logs a
                LEFT JOIN users u ON u.id = a.actor_user_id
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (StringUtils.hasText(targetType) && !"all".equalsIgnoreCase(targetType)) {
            sql.append(" WHERE LOWER(a.target_type) = :targetType");
            params.addValue("targetType", targetType.trim().toLowerCase(Locale.ROOT));
        }
        sql.append(" ORDER BY a.created_at DESC LIMIT :limit");
        params.addValue("limit", safeLimit);
        return namedParameterJdbcTemplate.query(sql.toString(), params, this::mapAudit);
    }

    @Transactional
    public void writeAudit(String actorUserId, String action, String targetType, String targetId, String oldValue, String newValue) {
        namedParameterJdbcTemplate.update("""
                INSERT INTO admin_audit_logs (actor_user_id, action, target_type, target_id, old_value_json, new_value_json)
                VALUES (
                    CAST(:actor AS uuid),
                    :action,
                    :targetType,
                    CASE WHEN :targetId IS NULL OR :targetId = '' THEN NULL ELSE CAST(:targetId AS uuid) END,
                    CAST(:oldValue AS jsonb),
                    CAST(:newValue AS jsonb)
                )
                """,
                new MapSqlParameterSource()
                        .addValue("actor", actorUserId)
                        .addValue("action", action)
                        .addValue("targetType", targetType)
                        .addValue("targetId", targetId)
                        .addValue("oldValue", toJsonValue(oldValue))
                        .addValue("newValue", toJsonValue(newValue))
        );
    }

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> listCategories(String status) {
        requireAdmin();
        StringBuilder sql = new StringBuilder("""
                SELECT id::text AS id, name, slug, parent_id::text AS parent_id, description, status, created_at, updated_at
                FROM categories
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status)) {
            sql.append(" WHERE LOWER(status) = :status");
            params.addValue("status", status.trim().toLowerCase(Locale.ROOT));
        }
        sql.append(" ORDER BY name ASC");
        return namedParameterJdbcTemplate.query(sql.toString(), params, this::mapCategory);
    }

    @Transactional
    public AdminCategoryResponse createCategory(AdminCategoryRequest request) {
        User admin = requireAdminUser();
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED", "Vui lòng nhập tên danh mục");
        }
        String name = request.name().trim();
        String slug = StringUtils.hasText(request.slug())
                ? slugify(request.slug())
                : slugify(name);
        String status = StringUtils.hasText(request.status())
                ? request.status().trim().toLowerCase(Locale.ROOT)
                : "active";
        if (!List.of("active", "inactive").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Trạng thái danh mục không hợp lệ");
        }

        String id = namedParameterJdbcTemplate.query("""
                INSERT INTO categories (name, slug, description, status)
                VALUES (:name, :slug, :description, :status)
                RETURNING id::text
                """,
                new MapSqlParameterSource()
                        .addValue("name", name)
                        .addValue("slug", slug)
                        .addValue("description", request.description() != null ? request.description().trim() : null)
                        .addValue("status", status),
                rs -> rs.next() ? rs.getString(1) : null
        );
        writeAudit(admin.getId().toString(), "CATEGORY_CREATE", "category", id, null, status);
        return findCategory(id);
    }

    @Transactional
    public AdminCategoryResponse updateCategory(String id, AdminCategoryRequest request) {
        User admin = requireAdminUser();
        ensureUuid(id, "Danh mục");
        AdminCategoryResponse current = findCategory(id);
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Thiếu dữ liệu cập nhật danh mục");
        }
        String name = StringUtils.hasText(request.name()) ? request.name().trim() : current.name();
        String slug = StringUtils.hasText(request.slug()) ? slugify(request.slug()) : current.slug();
        String description = request.description() != null ? request.description().trim() : current.description();
        String status = StringUtils.hasText(request.status())
                ? request.status().trim().toLowerCase(Locale.ROOT)
                : current.status();
        if (!List.of("active", "inactive").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Trạng thái danh mục không hợp lệ");
        }

        namedParameterJdbcTemplate.update("""
                UPDATE categories
                SET name = :name,
                    slug = :slug,
                    description = :description,
                    status = :status,
                    updated_at = now()
                WHERE id = CAST(:id AS uuid)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("name", name)
                        .addValue("slug", slug)
                        .addValue("description", description)
                        .addValue("status", status)
        );
        writeAudit(admin.getId().toString(), "CATEGORY_UPDATE", "category", id, current.status(), status);
        return findCategory(id);
    }

    @Transactional(readOnly = true)
    public List<AdminSettingResponse> listSettings() {
        requireAdmin();
        return namedParameterJdbcTemplate.query("""
                SELECT setting_key AS key, setting_value AS value, description, updated_at
                FROM system_settings
                ORDER BY setting_key ASC
                """, this::mapSetting);
    }

    @Transactional
    public List<AdminSettingResponse> updateSettings(AdminSettingsUpdateRequest request) {
        User admin = requireAdminUser();
        if (request == null || request.settings() == null || request.settings().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Không có cài đặt để cập nhật");
        }
        for (Map.Entry<String, String> entry : request.settings().entrySet()) {
            if (!StringUtils.hasText(entry.getKey())) {
                continue;
            }
            String key = entry.getKey().trim();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            int updated = namedParameterJdbcTemplate.update("""
                    UPDATE system_settings
                    SET setting_value = :value,
                        updated_at = now(),
                        updated_by = CAST(:actor AS uuid)
                    WHERE setting_key = :key
                    """,
                    new MapSqlParameterSource()
                            .addValue("key", key)
                            .addValue("value", value)
                            .addValue("actor", admin.getId().toString())
            );
            if (updated == 0) {
                namedParameterJdbcTemplate.update("""
                        INSERT INTO system_settings (setting_key, setting_value, description, updated_at, updated_by)
                        VALUES (:key, :value, :description, now(), CAST(:actor AS uuid))
                        ON CONFLICT (setting_key) DO UPDATE
                        SET setting_value = EXCLUDED.setting_value,
                            updated_at = now(),
                            updated_by = EXCLUDED.updated_by
                        """,
                        new MapSqlParameterSource()
                                .addValue("key", key)
                                .addValue("value", value)
                                .addValue("description", "Cập nhật từ admin")
                                .addValue("actor", admin.getId().toString()));
            }
            systemSettingsService.clearCache(key);
        }
        writeAudit(admin.getId().toString(), "SETTINGS_UPDATE", "system_settings", admin.getId().toString(), null, "updated");
        return listSettings();
    }

    private AdminPlanResponse findPlan(String id) {
        List<AdminPlanResponse> plans = namedParameterJdbcTemplate.query("""
                SELECT id::text AS id, name, target_role, description, price, currency, duration_days,
                       COALESCE(features::text, '{}') AS features_json, status, sort_order, created_at, updated_at
                FROM plans WHERE id = CAST(:id AS uuid)
                """, new MapSqlParameterSource("id", id), this::mapPlan);
        if (plans.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Không tìm thấy gói dịch vụ");
        }
        return plans.get(0);
    }

    private AdminSubscriptionResponse findSubscription(String id) {
        List<AdminSubscriptionResponse> items = namedParameterJdbcTemplate.query("""
                SELECT s.id::text AS id,
                       s.user_id::text AS user_id,
                       u.email AS user_email,
                       u.full_name AS user_name,
                       s.plan_id::text AS plan_id,
                       p.name AS plan_name,
                       s.status,
                       s.start_date,
                       s.end_date,
                       s.cancelled_at,
                       s.cancelled_reason,
                       s.created_at,
                       s.updated_at
                FROM subscriptions s
                JOIN users u ON u.id = s.user_id
                JOIN plans p ON p.id = s.plan_id
                WHERE s.id = CAST(:id AS uuid)
                """, new MapSqlParameterSource("id", id), this::mapSubscription);
        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND", "Không tìm thấy đăng ký");
        }
        return items.get(0);
    }

    private AdminCategoryResponse findCategory(String id) {
        List<AdminCategoryResponse> items = namedParameterJdbcTemplate.query("""
                SELECT id::text AS id, name, slug, parent_id::text AS parent_id, description, status, created_at, updated_at
                FROM categories WHERE id = CAST(:id AS uuid)
                """, new MapSqlParameterSource("id", id), this::mapCategory);
        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Không tìm thấy danh mục");
        }
        return items.get(0);
    }

    private AdminPlanResponse mapPlan(ResultSet rs, int rowNum) throws SQLException {
        return new AdminPlanResponse(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("target_role"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getString("currency"),
                rs.getInt("duration_days"),
                rs.getString("features_json"),
                rs.getString("status"),
                rs.getInt("sort_order"),
                toLocalDateTime(rs, "created_at"),
                toLocalDateTime(rs, "updated_at")
        );
    }

    private AdminSubscriptionResponse mapSubscription(ResultSet rs, int rowNum) throws SQLException {
        return new AdminSubscriptionResponse(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("user_email"),
                rs.getString("user_name"),
                rs.getString("plan_id"),
                rs.getString("plan_name"),
                rs.getString("status"),
                toLocalDateTime(rs, "start_date"),
                toLocalDateTime(rs, "end_date"),
                toLocalDateTime(rs, "cancelled_at"),
                rs.getString("cancelled_reason"),
                toLocalDateTime(rs, "created_at"),
                toLocalDateTime(rs, "updated_at")
        );
    }

    private AdminPaymentResponse mapPayment(ResultSet rs, int rowNum) throws SQLException {
        String gatewayResponse = rs.getString("gateway_response");
        String transferContent = rs.getString("gateway_order_id");
        String qrUrl = null;
        LocalDateTime expiresAt = null;
        if (StringUtils.hasText(gatewayResponse)) {
            try {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(gatewayResponse);
                if (node.hasNonNull("transferContent")) {
                    transferContent = node.get("transferContent").asText();
                }
                if (node.hasNonNull("qrUrl")) {
                    qrUrl = node.get("qrUrl").asText();
                }
                if (node.hasNonNull("expiresAt")) {
                    expiresAt = LocalDateTime.parse(node.get("expiresAt").asText().replace("Z", ""));
                }
            } catch (Exception ignored) {
            }
        }
        return new AdminPaymentResponse(
                rs.getString("id"),
                rs.getString("subscription_id"),
                rs.getString("user_id"),
                rs.getString("user_email"),
                rs.getString("plan_name"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("payment_method"),
                rs.getString("gateway"),
                rs.getString("status"),
                rs.getString("transaction_id"),
                rs.getString("failure_reason"),
                transferContent,
                qrUrl,
                toLocalDateTime(rs, "paid_at"),
                toLocalDateTime(rs, "created_at"),
                expiresAt
        );
    }

    private AdminAuditLogResponse mapAudit(ResultSet rs, int rowNum) throws SQLException {
        return new AdminAuditLogResponse(
                rs.getString("id"),
                rs.getString("actor_user_id"),
                rs.getString("actor_email"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getString("old_value_json"),
                rs.getString("new_value_json"),
                rs.getString("ip_address"),
                toLocalDateTime(rs, "created_at")
        );
    }

    private AdminCategoryResponse mapCategory(ResultSet rs, int rowNum) throws SQLException {
        return new AdminCategoryResponse(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("parent_id"),
                rs.getString("description"),
                rs.getString("status"),
                toLocalDateTime(rs, "created_at"),
                toLocalDateTime(rs, "updated_at")
        );
    }

    private AdminSettingResponse mapSetting(ResultSet rs, int rowNum) throws SQLException {
        return new AdminSettingResponse(
                rs.getString("key"),
                rs.getString("value"),
                rs.getString("description"),
                toLocalDateTime(rs, "updated_at")
        );
    }

    private long count(String sql) {
        Long value = namedParameterJdbcTemplate.getJdbcTemplate().queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private BigDecimal money(String sql) {
        BigDecimal value = namedParameterJdbcTemplate.getJdbcTemplate().queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String toJsonValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "null";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || "null".equals(trimmed)
                || "true".equals(trimmed) || "false".equals(trimmed)
                || trimmed.matches("-?\\d+(\\.\\d+)?")) {
            return trimmed;
        }
        return "\"" + trimmed.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String slugify(String value) {
        String slug = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (!StringUtils.hasText(slug)) {
            slug = "category-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return slug;
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
        UUID userId;
        try {
            userId = UUID.fromString(current.id());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Không tìm thấy người dùng quản trị");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Không tìm thấy người dùng quản trị"));
    }
}
