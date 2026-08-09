package com.sjp.recruitment.service;

import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRankingScheduler {

    private final ApplicationRepository applicationRepository;
    private final AiRankingService aiRankingService;

    // Disabled auto-ranking per user request
    // @Scheduled(fixedDelay = 300000)
    public void processPendingRankings() {
        log.info("Starting AI ranking scheduler for pending applications");
        
        try {
            List<Application> pendingApps = applicationRepository.findByNeedRerankTrue();
            
            if (pendingApps != null && !pendingApps.isEmpty()) {
                log.info("Found {} applications needing re-ranking", pendingApps.size());
                for (Application app : pendingApps) {
                    try {
                        aiRankingService.rankApplication(app.getId());
                        log.info("Successfully re-ranked application {}", app.getId());
                    } catch (Exception e) {
                        log.error("Error re-ranking application {}", app.getId(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error in AI ranking scheduler", e);
        }
    }
}
