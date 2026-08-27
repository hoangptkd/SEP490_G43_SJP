package com.sjp.recruitment.scheduler;

import com.sjp.recruitment.config.AiJobSearchProperties;
import com.sjp.recruitment.repository.AiJobSearchRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiJobSearchRetentionScheduler {
    private final AiJobSearchRunRepository runRepository;
    private final AiJobSearchProperties properties;

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void cleanup() {
        long deleted = runRepository.deleteByStatusAndCreatedAtBefore(
                "FAILED", LocalDateTime.now().minusDays(properties.getRetentionDays()));
        if (deleted > 0) {
            log.info("Cleaned {} failed AI job search run metadata rows", deleted);
        }
    }
}
