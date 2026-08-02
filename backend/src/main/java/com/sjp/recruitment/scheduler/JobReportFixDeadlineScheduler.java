package com.sjp.recruitment.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobReportFixDeadlineScheduler {

    private static final String AUTO_REMOVE_NOTE =
            "Hết hạn 3 ngày chỉnh sửa sau khi Admin yêu cầu — hệ thống đã tự động gỡ tin.";

    private final NamedParameterJdbcTemplate jdbc;

    @Scheduled(cron = "0 10 0 * * ?")
    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void autoRemoveExpiredReportFixes() {
        List<String> jobIds = jdbc.query("""
                SELECT id::text
                FROM jobs
                WHERE status = 'awaiting_company'
                  AND report_fix_deadline IS NOT NULL
                  AND report_fix_deadline < now()
                """,
                new MapSqlParameterSource(),
                (rs, rowNum) -> rs.getString(1)
        );

        if (jobIds.isEmpty()) {
            return;
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("note", AUTO_REMOVE_NOTE)
                .addValue("jobIds", jobIds);

        int removedJobs = jdbc.update("""
                UPDATE jobs
                SET status = 'removed',
                    closed_at = now(),
                    rejection_reason = :note,
                    report_fix_deadline = NULL,
                    updated_at = now()
                WHERE id::text IN (:jobIds)
                  AND status = 'awaiting_company'
                """, params);

        int resolvedReports = jdbc.update("""
                UPDATE job_reports
                SET status = 'resolved',
                    admin_note = COALESCE(admin_note, :note),
                    company_fix_deadline = NULL,
                    resolved_at = now()
                WHERE job_id::text IN (:jobIds)
                  AND status = 'awaiting_company'
                """, params);

        for (String jobId : jobIds) {
            jdbc.update("""
                    INSERT INTO admin_audit_logs (actor_user_id, action, target_type, target_id, old_value_json, new_value_json)
                    VALUES (
                        NULL,
                        'JOB_REPORT_AUTO_REMOVE',
                        'job',
                        CAST(:jobId AS uuid),
                        CAST('"awaiting_company"' AS jsonb),
                        CAST('"removed"' AS jsonb)
                    )
                    """,
                    new MapSqlParameterSource("jobId", jobId)
            );
        }

        log.info(
                "JobReportFixDeadlineScheduler: auto-removed {} jobs past 3-day fix deadline (resolved {} reports)",
                removedJobs,
                resolvedReports
        );
    }
}
