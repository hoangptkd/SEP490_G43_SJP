package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.FeatureUsageResponse;
import com.sjp.recruitment.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeatureLimitService {

    public static final String JOB_POSTS = "job_posts";
    public static final String CV_UPLOADS = "cv_uploads";
    public static final String APPLICATIONS = "applications";
    public static final String AI_SESSIONS = "ai_sessions";
    public static final String AI_JOB_SEARCHES = "ai_job_searches";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Ưu tiên hiển thị tin (>= 1 thì hiện badge Nổi bật). Gợi ý: 0 free, 1/2/3 theo bậc gói. */
    public static final int FEATURED_PRIORITY_THRESHOLD = 1;

    /**
     * Điểm ưu tiên từ gói active của employer tạo tin.
     * Free / không gói = 0.
     */
    public static final String LISTING_PRIORITY_SQL = """
            COALESCE((
                SELECT CASE
                    WHEN (p.features->>'listingPriority') ~ '^[0-9]+$'
                        THEN (p.features->>'listingPriority')::int
                    ELSE 0
                END
                FROM employers e
                JOIN subscriptions s ON s.user_id = e.user_id
                  AND LOWER(s.status) = 'active'
                  AND (s.end_date IS NULL OR s.end_date > now())
                JOIN plans p ON p.id = s.plan_id
                  AND LOWER(COALESCE(p.target_role, '')) IN ('employer', 'all')
                  AND LOWER(COALESCE(p.status, 'active')) = 'active'
                WHERE e.id = j.created_by_employer_id
                ORDER BY s.start_date DESC NULLS LAST, s.created_at DESC
                LIMIT 1
            ), 0)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public void requireJobPost(User user) {
        int limit = resolveLimit(user, "maxJobs", "max_free_job_posts", 20);
        int used = countEmployerJobs(user.getId());
        enforce(user, used, limit,
                "Bạn đã đạt giới hạn tối đa " + limit + " tin tuyển dụng đang mở cùng lúc (" + used + "/" + limit + "). Vui lòng đóng bớt tin khác hoặc nâng cấp gói dịch vụ mới có thể đăng tin mới hoặc mở lại tin cũ.");
    }

    @Transactional(readOnly = true)
    public int resolveMaxJobPostingDays(User user) {
        return resolveLimit(user, "maxJobPostingDays", "max_free_job_posting_days", 30);
    }

    @Transactional(readOnly = true)
    public void requireValidJobDeadline(User user, java.time.LocalDate deadline) {
        if (deadline == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEADLINE_REQUIRED", "Hạn nộp hồ sơ là bắt buộc.");
        }
        java.time.LocalDate today = java.time.LocalDate.now(BUSINESS_ZONE);
        if (deadline.isBefore(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEADLINE_IN_PAST", "Hạn nộp hồ sơ không được ở trong quá khứ.");
        }
        int maxDays = resolveMaxJobPostingDays(user);
        if (maxDays > 0) {
            java.time.LocalDate maxAllowedDate = today.plusDays(maxDays);
            if (deadline.isAfter(maxAllowedDate)) {
                String dateFormatted = maxAllowedDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                throw new ApiException(HttpStatus.BAD_REQUEST, "DEADLINE_EXCEEDS_PLAN_LIMIT",
                        "Gói dịch vụ hiện tại chỉ cho phép chọn Hạn nộp hồ sơ tối đa " + maxDays + " ngày từ hôm nay (đến ngày " + dateFormatted + "). Vui lòng chọn ngày hợp lệ hoặc nâng cấp gói dịch vụ.");
            }
        }
    }

    @Transactional
    public void consumeJobPost(User user) {
        bumpUsage(user, JOB_POSTS, "maxJobs", "max_free_job_posts", 15, false);
    }

    @Transactional(readOnly = true)
    public void requireCvUpload(User user) {
        int limit = resolveLimit(user, "maxCv", null, 3);
        int used = countCandidateCvs(user.getId());
        enforce(user, used, limit,
                "Bạn đã sử dụng hết lượt tải lên CV (" + used + "/" + limit + ").");
    }

    @Transactional
    public void consumeCvUpload(User user) {
        bumpUsage(user, CV_UPLOADS, "maxCv", null, 3, false);
    }

    @Transactional(readOnly = true)
    public void requireApplication(User user) {
        int limit = resolveLimit(user, "maxApplicationsPerDay", "max_applications_per_day", 5);
        int used = countApplicationsToday(user.getId());
        enforce(user, used, limit,
                "Bạn đã đạt giới hạn ứng tuyển hôm nay (" + used + "/" + limit + ").");
    }

    @Transactional
    public void consumeApplication(User user) {
        bumpUsage(user, APPLICATIONS, "maxApplicationsPerDay", "max_applications_per_day", 5, true);
    }

    @Transactional(readOnly = true)
    public void requireAiSession(User user) {
        int limit = resolveLimit(user, "maxAiSessionsPerDay", "max_ai_sessions_per_day", 3);
        int used = countAiSessionsToday(user.getId());
        enforce(user, used, limit,
                "Bạn đã đạt giới hạn phiên AI hôm nay (" + used + "/" + limit + ").");
    }

    @Transactional
    public void consumeAiSession(User user) {
        bumpUsage(user, AI_SESSIONS, "maxAiSessionsPerDay", "max_ai_sessions_per_day", 3, true);
    }

    @Transactional(readOnly = true)
    public AiJobSearchQuota getAiJobSearchQuota(User user) {
        OffsetDateTime now = OffsetDateTime.now(BUSINESS_ZONE);
        OffsetDateTime start = now.withDayOfMonth(1).toLocalDate().atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        OffsetDateTime resetAt = start.plusMonths(1);
        int used = countAiJobSearchesSince(user.getId(), start);
        int limit = resolveLimit(user, "maxAiJobSearchesPerMonth", "max_ai_job_searches_per_month", 3);
        int remaining = limit < 0 ? -1 : Math.max(0, limit - used);
        return new AiJobSearchQuota(used, limit, remaining, resetAt);
    }

    @Transactional(readOnly = true)
    public void requireAiJobSearch(User user) {
        AiJobSearchQuota quota = getAiJobSearchQuota(user);
        enforce(user, quota.used(), quota.limit(),
                "Bạn đã đạt giới hạn tìm việc bằng AI trong tháng này (" + quota.used() + "/" + quota.limit() + ").");
    }

    @Transactional
    public void consumeAiJobSearch(User user) {
        String subscriptionId = findActiveSubscriptionId(user.getId());
        if (subscriptionId == null) {
            return;
        }
        int limit = resolveLimit(user, "maxAiJobSearchesPerMonth", "max_ai_job_searches_per_month", 3);
        upsertMonthlyUsage(subscriptionId, AI_JOB_SEARCHES, limit);
        jdbc.update("""
                        UPDATE subscription_usages
                        SET used_count = used_count + 1,
                            updated_at = now()
                        WHERE subscription_id = CAST(:subscriptionId AS uuid)
                          AND feature_key = :featureKey
                        """,
                new MapSqlParameterSource()
                        .addValue("subscriptionId", subscriptionId)
                        .addValue("featureKey", AI_JOB_SEARCHES));
    }

    /**
     * Snapshot hạn mức theo gói đang active (hoặc free settings) — dùng live count.
     */
    @Transactional(readOnly = true)
    public List<FeatureUsageResponse> getUsageSummary(User user) {
        List<FeatureUsageResponse> rows = new ArrayList<>();
        User.UserRole role = user.getRoleEnum();
        if (role == User.UserRole.EMPLOYER || role == User.UserRole.ADMIN) {
            rows.add(usage(
                    JOB_POSTS,
                    "Tin đăng đang mở",
                    countEmployerJobs(user.getId()),
                    resolveLimit(user, "maxJobs", "max_free_job_posts", 15),
                    false
            ));
        }
        if (role == User.UserRole.CANDIDATE || role == User.UserRole.ADMIN) {
            rows.add(usage(
                    CV_UPLOADS,
                    "CV đã tải lên",
                    countCandidateCvs(user.getId()),
                    resolveLimit(user, "maxCv", null, 3),
                    false
            ));
            rows.add(usage(
                    APPLICATIONS,
                    "Ứng tuyển hôm nay",
                    countApplicationsToday(user.getId()),
                    resolveLimit(user, "maxApplicationsPerDay", "max_applications_per_day", 5),
                    true
            ));
            rows.add(usage(
                    AI_SESSIONS,
                    "Phiên AI hôm nay",
                    countAiSessionsToday(user.getId()),
                    resolveLimit(user, "maxAiSessionsPerDay", "max_ai_sessions_per_day", 3),
                    true
            ));
            AiJobSearchQuota aiJobSearchQuota = getAiJobSearchQuota(user);
            rows.add(usage(
                    AI_JOB_SEARCHES,
                    "Tìm việc bằng AI trong tháng",
                    aiJobSearchQuota.used(),
                    aiJobSearchQuota.limit(),
                    false
            ));
        }
        return rows;
    }

    @Transactional
    public void initUsagesForSubscription(String subscriptionId, String featuresJson) {
        upsertUsage(subscriptionId, JOB_POSTS, featureInt(featuresJson, "maxJobs", 20), false);
        upsertUsage(subscriptionId, CV_UPLOADS, featureInt(featuresJson, "maxCv", 10), false);
        upsertUsage(subscriptionId, APPLICATIONS, featureInt(featuresJson, "maxApplicationsPerDay", 50), true);
        upsertUsage(subscriptionId, AI_SESSIONS, featureInt(featuresJson, "maxAiSessionsPerDay", 20), true);
        upsertMonthlyUsage(subscriptionId, AI_JOB_SEARCHES, featureInt(featuresJson, "maxAiJobSearchesPerMonth", 10));
    }

    /** Khi admin sửa hạn mức gói → cập nhật limit_count cho mọi subscription đang active của gói đó. */
    @Transactional
    public void syncUsagesForPlan(String planId, String featuresJson) {
        if (!StringUtils.hasText(planId)) {
            return;
        }
        List<String> subscriptionIds = jdbc.query("""
                        SELECT id::text
                        FROM subscriptions
                        WHERE plan_id = CAST(:planId AS uuid)
                          AND status = 'active'
                          AND (end_date IS NULL OR end_date > now())
                        """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> rs.getString(1));
        for (String subscriptionId : subscriptionIds) {
            initUsagesForSubscription(subscriptionId, featuresJson == null ? "{}" : featuresJson);
        }
    }

    private FeatureUsageResponse usage(String key, String label, int used, int limit, boolean daily) {
        return new FeatureUsageResponse(key, label, used, limit, daily);
    }

    private void bumpUsage(User user, String featureKey, String planFeatureKey, String freeSettingKey,
                           int freeDefault, boolean daily) {
        String subscriptionId = findActiveSubscriptionId(user.getId());
        if (subscriptionId == null) {
            return;
        }
        int limit = resolveLimit(user, planFeatureKey, freeSettingKey, freeDefault);
        upsertUsage(subscriptionId, featureKey, limit, daily);
        jdbc.update("""
                        UPDATE subscription_usages
                        SET used_count = used_count + 1,
                            updated_at = now()
                        WHERE subscription_id = CAST(:subscriptionId AS uuid)
                          AND feature_key = :featureKey
                        """,
                new MapSqlParameterSource()
                        .addValue("subscriptionId", subscriptionId)
                        .addValue("featureKey", featureKey));
    }

    private void upsertUsage(String subscriptionId, String featureKey, int limitCount, boolean daily) {
        jdbc.update("""
                        INSERT INTO subscription_usages (id, subscription_id, feature_key, used_count, limit_count, reset_at, updated_at)
                        VALUES (gen_random_uuid(), CAST(:subscriptionId AS uuid), :featureKey, 0, :limitCount,
                                CASE WHEN :daily THEN date_trunc('day', now()) + interval '1 day' ELSE NULL END,
                                now())
                        ON CONFLICT (subscription_id, feature_key) DO UPDATE
                        SET limit_count = EXCLUDED.limit_count,
                            reset_at = CASE
                                WHEN :daily AND (subscription_usages.reset_at IS NULL OR subscription_usages.reset_at <= now())
                                    THEN date_trunc('day', now()) + interval '1 day'
                                ELSE subscription_usages.reset_at
                            END,
                            used_count = CASE
                                WHEN :daily AND (subscription_usages.reset_at IS NULL OR subscription_usages.reset_at <= now())
                                    THEN 0
                                ELSE subscription_usages.used_count
                            END,
                            updated_at = now()
                        """,
                new MapSqlParameterSource()
                        .addValue("subscriptionId", subscriptionId)
                        .addValue("featureKey", featureKey)
                        .addValue("limitCount", limitCount)
                        .addValue("daily", daily));
    }

    private void upsertMonthlyUsage(String subscriptionId, String featureKey, int limitCount) {
        jdbc.update("""
                        INSERT INTO subscription_usages (id, subscription_id, feature_key, used_count, limit_count, reset_at, updated_at)
                        VALUES (gen_random_uuid(), CAST(:subscriptionId AS uuid), :featureKey, 0, :limitCount,
                                (date_trunc('month', now() AT TIME ZONE 'Asia/Ho_Chi_Minh') + interval '1 month') AT TIME ZONE 'Asia/Ho_Chi_Minh',
                                now())
                        ON CONFLICT (subscription_id, feature_key) DO UPDATE
                        SET limit_count = EXCLUDED.limit_count,
                            reset_at = CASE
                                WHEN subscription_usages.reset_at IS NULL OR subscription_usages.reset_at <= now()
                                    THEN EXCLUDED.reset_at
                                ELSE subscription_usages.reset_at
                            END,
                            used_count = CASE
                                WHEN subscription_usages.reset_at IS NULL OR subscription_usages.reset_at <= now()
                                    THEN 0
                                ELSE subscription_usages.used_count
                            END,
                            updated_at = now()
                        """,
                new MapSqlParameterSource()
                        .addValue("subscriptionId", subscriptionId)
                        .addValue("featureKey", featureKey)
                        .addValue("limitCount", limitCount));
    }

    private int resolveLimit(User user, String planFeatureKey, String freeSettingKey, int freeDefault) {
        ActivePlan plan = findActivePlan(user.getId());
        if (plan != null) {
            // Có gói active: đọc đúng key từ features JSON admin cấu hình (không nhân 5 lần free)
            return featureInt(plan.featuresJson(), planFeatureKey, freeDefault);
        }
        if (StringUtils.hasText(freeSettingKey)) {
            return readSettingInt(freeSettingKey, freeDefault);
        }
        return freeDefault;
    }

    private void enforce(User user, int used, int limit, String baseMessage) {
        if (limit < 0 || used < limit) {
            return;
        }
        boolean onFreePlan = findActivePlan(user.getId()) == null;
        String tip = onFreePlan
                ? " Bạn đang dùng hạn mức miễn phí. Hãy xem các gói dịch vụ để tăng thêm số lượng."
                : " Hãy nâng cấp hoặc đổi gói dịch vụ để tăng thêm số lượng.";
        throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "PLAN_LIMIT_REACHED", baseMessage + tip);
    }

    private ActivePlan findActivePlan(UUID userId) {
        var rows = jdbc.query("""
                        SELECT s.id::text AS subscription_id, COALESCE(p.features::text, '{}') AS features_json
                        FROM subscriptions s
                        JOIN plans p ON p.id = s.plan_id
                        WHERE s.user_id = CAST(:userId AS uuid)
                          AND LOWER(s.status) = 'active'
                          AND (s.end_date IS NULL OR s.end_date > now())
                          AND LOWER(COALESCE(p.status, 'active')) = 'active'
                        ORDER BY s.start_date DESC NULLS LAST
                        LIMIT 1
                        """,
                new MapSqlParameterSource("userId", userId.toString()),
                (rs, rowNum) -> new ActivePlan(rs.getString("subscription_id"), rs.getString("features_json")));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String findActiveSubscriptionId(UUID userId) {
        ActivePlan plan = findActivePlan(userId);
        return plan == null ? null : plan.subscriptionId();
    }

    @Transactional(readOnly = true)
    public boolean hasActivePaidPlan(User user) {
        if (user == null || user.getId() == null) return false;
        return findActivePlan(user.getId()) != null;
    }

    public Integer featureIntOrNull(String featuresJson, String key) {
        if (!StringUtils.hasText(featuresJson) || !StringUtils.hasText(key)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(featuresJson).get(key);
            if (node != null && node.isNumber()) {
                return node.asInt();
            }
            if (node != null && node.isTextual() && node.asText().matches("-?\\d+")) {
                return Integer.parseInt(node.asText());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public int featureInt(String featuresJson, String key, int defaultValue) {
        Integer value = featureIntOrNull(featuresJson, key);
        return value == null ? defaultValue : value;
    }

    /** Ưu tiên tin đăng theo gói active của user employer (0 nếu free). */
    @Transactional(readOnly = true)
    public int resolveListingPriorityForUser(UUID userId) {
        if (userId == null) {
            return 0;
        }
        ActivePlan plan = findActivePlan(userId);
        if (plan == null) {
            return 0;
        }
        return featureInt(plan.featuresJson(), "listingPriority", 0);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Integer> resolveListingPrioritiesForUsers(Collection<UUID> userIds) {
        List<UUID> distinctUserIds = userIds == null
                ? List.of()
                : userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinctUserIds.isEmpty()) {
            return Map.of();
        }
        List<ActivePlanForUser> plans = jdbc.query("""
                        SELECT DISTINCT ON (s.user_id)
                               s.user_id::text AS user_id,
                               COALESCE(p.features::text, '{}') AS features_json
                        FROM subscriptions s
                        JOIN plans p ON p.id = s.plan_id
                        WHERE s.user_id IN (:userIds)
                          AND LOWER(s.status) = 'active'
                          AND (s.end_date IS NULL OR s.end_date > now())
                          AND LOWER(COALESCE(p.target_role, '')) IN ('employer', 'all')
                          AND LOWER(COALESCE(p.status, 'active')) = 'active'
                        ORDER BY s.user_id, s.start_date DESC NULLS LAST
                        """,
                new MapSqlParameterSource("userIds", distinctUserIds),
                (rs, rowNum) -> new ActivePlanForUser(
                        UUID.fromString(rs.getString("user_id")),
                        rs.getString("features_json")
                ));
        Map<UUID, Integer> priorities = new LinkedHashMap<>();
        plans.forEach(plan -> priorities.put(
                plan.userId(),
                featureInt(plan.featuresJson(), "listingPriority", 0)
        ));
        return priorities;
    }

    public static boolean isFeatured(int listingPriority) {
        return listingPriority >= FEATURED_PRIORITY_THRESHOLD;
    }

    private int readSettingInt(String key, int defaultValue) {
        Integer value = jdbc.query("""
                        SELECT setting_value FROM system_settings WHERE setting_key = :key LIMIT 1
                        """,
                new MapSqlParameterSource("key", key),
                rs -> rs.next() ? parseIntSafe(rs.getString(1), defaultValue) : defaultValue);
        return value == null ? defaultValue : value;
    }

    private int parseIntSafe(String raw, int defaultValue) {
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim().replace("\"", ""));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private int countEmployerJobs(UUID userId) {
        Long count = jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM jobs j
                        JOIN employers e ON e.id = j.created_by_employer_id
                        WHERE e.user_id = CAST(:userId AS uuid)
                          AND LOWER(j.status) NOT IN ('closed', 'removed', 'expired', 'archived')
                        """,
                new MapSqlParameterSource("userId", userId.toString()),
                Long.class);
        return count == null ? 0 : count.intValue();
    }

    private int countCandidateCvs(UUID userId) {
        Long count = jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM resumes r
                        JOIN job_seekers js ON js.id = r.job_seeker_id
                        WHERE js.user_id = CAST(:userId AS uuid)
                          AND r.deleted_at IS NULL
                          AND LOWER(COALESCE(r.source_type, 'uploaded')) = 'uploaded'
                        """,
                new MapSqlParameterSource("userId", userId.toString()),
                Long.class);
        return count == null ? 0 : count.intValue();
    }

    private int countApplicationsToday(UUID userId) {
        Long count = jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM applications a
                        JOIN job_seekers js ON js.id = a.job_seeker_id
                        WHERE js.user_id = CAST(:userId AS uuid)
                          AND a.applied_at >= date_trunc('day', now())
                        """,
                new MapSqlParameterSource("userId", userId.toString()),
                Long.class);
        return count == null ? 0 : count.intValue();
    }

    private int countAiSessionsToday(UUID userId) {
        Long count = jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM interview_sessions s
                        JOIN job_seekers js ON js.id = s.job_seeker_id
                        WHERE js.user_id = CAST(:userId AS uuid)
                          AND s.created_at >= date_trunc('day', now())
                          AND s.deleted_at IS NULL
                        """,
                new MapSqlParameterSource("userId", userId.toString()),
                Long.class);
        return count == null ? 0 : count.intValue();
    }

    private int countAiJobSearchesSince(UUID userId, OffsetDateTime start) {
        Long count = jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM ai_job_search_runs r
                        JOIN job_seekers js ON js.id = r.job_seeker_id
                        WHERE js.user_id = CAST(:userId AS uuid)
                          AND r.quota_consumed = true
                          AND r.created_at >= :start
                        """,
                new MapSqlParameterSource()
                        .addValue("userId", userId.toString())
                        .addValue("start", start),
                Long.class);
        return count == null ? 0 : count.intValue();
    }

    public record AiJobSearchQuota(int used, int limit, int remaining, OffsetDateTime resetAt) {
    }

    private record ActivePlan(String subscriptionId, String featuresJson) {
    }

    private record ActivePlanForUser(UUID userId, String featuresJson) {
    }
}
