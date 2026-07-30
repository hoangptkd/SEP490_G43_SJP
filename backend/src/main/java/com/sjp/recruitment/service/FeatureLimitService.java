package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeatureLimitService {

    public static final String JOB_POSTS = "job_posts";
    public static final String CV_UPLOADS = "cv_uploads";
    public static final String APPLICATIONS = "applications";
    public static final String AI_SESSIONS = "ai_sessions";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public void requireJobPost(User user) {
        int limit = resolveLimit(user, JOB_POSTS, "maxJobs", "max_free_job_posts", 3);
        int used = countEmployerJobs(user.getId());
        enforce(used, limit, "Bạn đã đạt giới hạn đăng tin (" + used + "/" + limit + "). Vui lòng nâng cấp gói.");
    }

    @Transactional
    public void consumeJobPost(User user) {
        bumpUsage(user, JOB_POSTS, "maxJobs", "max_free_job_posts", 3, false);
    }

    @Transactional(readOnly = true)
    public void requireCvUpload(User user) {
        int limit = resolveLimit(user, CV_UPLOADS, "maxCv", null, 3);
        int used = countCandidateCvs(user.getId());
        enforce(used, limit, "Bạn đã đạt giới hạn số CV (" + used + "/" + limit + "). Vui lòng nâng cấp gói.");
    }

    @Transactional
    public void consumeCvUpload(User user) {
        bumpUsage(user, CV_UPLOADS, "maxCv", null, 3, false);
    }

    @Transactional(readOnly = true)
    public void requireApplication(User user) {
        int limit = resolveLimit(user, APPLICATIONS, "maxApplicationsPerDay", "max_applications_per_day", 20);
        int used = countApplicationsToday(user.getId());
        enforce(used, limit, "Bạn đã đạt giới hạn ứng tuyển hôm nay (" + used + "/" + limit + "). Vui lòng nâng cấp gói hoặc thử lại ngày mai.");
    }

    @Transactional
    public void consumeApplication(User user) {
        bumpUsage(user, APPLICATIONS, "maxApplicationsPerDay", "max_applications_per_day", 20, true);
    }

    @Transactional(readOnly = true)
    public void requireAiSession(User user) {
        int limit = resolveLimit(user, AI_SESSIONS, "maxAiSessionsPerDay", "max_ai_sessions_per_day", 5);
        int used = countAiSessionsToday(user.getId());
        enforce(used, limit, "Bạn đã đạt giới hạn phiên AI hôm nay (" + used + "/" + limit + "). Vui lòng nâng cấp gói hoặc thử lại ngày mai.");
    }

    @Transactional
    public void consumeAiSession(User user) {
        bumpUsage(user, AI_SESSIONS, "maxAiSessionsPerDay", "max_ai_sessions_per_day", 5, true);
    }

    @Transactional
    public void initUsagesForSubscription(String subscriptionId, String featuresJson) {
        upsertUsage(subscriptionId, JOB_POSTS, featureInt(featuresJson, "maxJobs", 20), false);
        upsertUsage(subscriptionId, CV_UPLOADS, featureInt(featuresJson, "maxCv", 10), false);
        upsertUsage(subscriptionId, APPLICATIONS, featureInt(featuresJson, "maxApplicationsPerDay", 50), true);
        upsertUsage(subscriptionId, AI_SESSIONS, featureInt(featuresJson, "maxAiSessionsPerDay", 20), true);
    }

    private void bumpUsage(User user, String featureKey, String planFeatureKey, String freeSettingKey, int freeDefault, boolean daily) {
        String subscriptionId = findActiveSubscriptionId(user.getId());
        if (subscriptionId == null) {
            return;
        }
        int limit = resolveLimit(user, featureKey, planFeatureKey, freeSettingKey, freeDefault);
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

    private int resolveLimit(User user, String featureKey, String planFeatureKey, String freeSettingKey, int freeDefault) {
        ActivePlan plan = findActivePlan(user.getId());
        if (plan != null) {
            return featureInt(plan.featuresJson(), planFeatureKey, freeDefault * 5);
        }
        if (StringUtils.hasText(freeSettingKey)) {
            return readSettingInt(freeSettingKey, freeDefault);
        }
        return freeDefault;
    }

    private void enforce(int used, int limit, String message) {
        if (limit >= 0 && used >= limit) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "PLAN_LIMIT_REACHED", message);
        }
    }

    private ActivePlan findActivePlan(UUID userId) {
        var rows = jdbc.query("""
                        SELECT s.id::text AS subscription_id, COALESCE(p.features::text, '{}') AS features_json
                        FROM subscriptions s
                        JOIN plans p ON p.id = s.plan_id
                        WHERE s.user_id = CAST(:userId AS uuid)
                          AND s.status = 'active'
                          AND (s.end_date IS NULL OR s.end_date > now())
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

    private int featureInt(String featuresJson, String key, int defaultValue) {
        if (!StringUtils.hasText(featuresJson) || !StringUtils.hasText(key)) {
            return defaultValue;
        }
        try {
            JsonNode node = objectMapper.readTree(featuresJson).get(key);
            if (node != null && node.isNumber()) {
                return Math.max(0, node.asInt());
            }
            if (node != null && node.isTextual() && node.asText().matches("\\d+")) {
                return Integer.parseInt(node.asText());
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
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
                          AND LOWER(j.status) NOT IN ('closed', 'removed', 'expired')
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
                          AND a.submitted_at >= date_trunc('day', now())
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

    private record ActivePlan(String subscriptionId, String featuresJson) {
    }
}
