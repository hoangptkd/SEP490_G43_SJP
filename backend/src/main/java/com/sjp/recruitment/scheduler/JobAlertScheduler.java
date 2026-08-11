package com.sjp.recruitment.scheduler;

import com.sjp.recruitment.service.JobAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class JobAlertScheduler {
    private final JobAlertService jobAlertService;

    @Scheduled(cron = "${app.job-alerts.cron:0 15 * * * *}")
    public void processAlerts() {
        jobAlertService.processDueAlerts();
    }
}
