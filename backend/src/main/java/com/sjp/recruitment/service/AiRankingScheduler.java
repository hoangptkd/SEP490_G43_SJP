package com.sjp.recruitment.service;

import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRankingScheduler {

    private final ApplicationRepository applicationRepository;
    private final AiRankingService aiRankingService;

    // Run every 5 minutes
    @Scheduled(fixedDelay = 300000)
    public void processPendingRankings() {
        log.info("Starting AI ranking scheduler for pending applications");
        
        try {
            // Find applications that need reranking. In a real app, this should be paginated and have a status check
            // For now, let's find all applications where need_rerank is true
            // Since we don't have a custom query in repository yet, we can fetch all or add a method.
            // Let's assume we can fetch them using a custom repository method `findByNeedRerankTrue()`
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
