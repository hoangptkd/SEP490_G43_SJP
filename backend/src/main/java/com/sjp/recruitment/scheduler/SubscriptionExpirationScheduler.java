package com.sjp.recruitment.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpirationScheduler {

    private final NamedParameterJdbcTemplate jdbc;

    @Scheduled(cron = "0 5 0 * * ?")
    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void expireSubscriptions() {
        int updated = jdbc.update("""
                        UPDATE subscriptions
                        SET status = 'expired',
                            updated_at = now()
                        WHERE status = 'active'
                          AND end_date IS NOT NULL
                          AND end_date < now()
                        """,
                new MapSqlParameterSource());
        if (updated > 0) {
            log.info("SubscriptionExpirationScheduler: expired {} subscriptions", updated);
        }

        int expiredPayments = jdbc.update("""
                        UPDATE payments p
                        SET status = 'cancelled',
                            failure_reason = 'Hết hạn thanh toán chuyển khoản'
                        WHERE p.status = 'pending'
                          AND p.payment_method = 'bank_transfer'
                          AND (p.gateway_response ->> 'expiresAt') IS NOT NULL
                          AND (p.gateway_response ->> 'expiresAt')::timestamptz < now()
                        """,
                new MapSqlParameterSource());
        if (expiredPayments > 0) {
            jdbc.update("""
                            UPDATE subscriptions s
                            SET status = 'cancelled',
                                cancelled_at = now(),
                                cancelled_reason = 'Hết hạn thanh toán chuyển khoản',
                                updated_at = now()
                            WHERE s.status = 'pending'
                              AND EXISTS (
                                SELECT 1 FROM payments p
                                WHERE p.subscription_id = s.id
                                  AND p.status = 'cancelled'
                                  AND p.failure_reason = 'Hết hạn thanh toán chuyển khoản'
                              )
                            """,
                    new MapSqlParameterSource());
            log.info("SubscriptionExpirationScheduler: cancelled {} expired bank-transfer payments", expiredPayments);
        }
    }
}
