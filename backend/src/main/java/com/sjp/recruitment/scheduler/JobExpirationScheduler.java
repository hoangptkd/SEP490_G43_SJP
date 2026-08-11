package com.sjp.recruitment.scheduler;

import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.repository.JobRepository;
import com.sjp.recruitment.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class JobExpirationScheduler {

    private final JobRepository jobRepository;
    private final JobService jobService;

    @Scheduled(cron = "0 0 0 * * ?") // Chạy vào nửa đêm mỗi ngày
    @Scheduled(fixedDelay = 3600000) // Chạy lặp mỗi 1 giờ để đảm bảo xử lý kịp thời
    @Transactional
    public void checkAndCloseExpiredJobs() {
        LocalDate today = LocalDate.now();
        List<Job> expiredJobs = jobRepository.findExpiredPublishedJobs(today);
        if (!expiredJobs.isEmpty()) {
            log.info("JobExpirationScheduler: Found {} expired jobs to close.", expiredJobs.size());
            for (Job job : expiredJobs) {
                job.setStatus("closed");
                job.setClosedAt(LocalDateTime.now());
                jobRepository.save(job);
                try {
                    jobService.notifyCandidatesJobClosed(job, "Tin tuyển dụng [" + job.getTitle() + "] mà bạn ứng tuyển đã hết hạn nộp hồ sơ và tự động đóng.");
                } catch (Exception e) {
                    log.error("Failed to notify candidates for closed job {}", job.getId(), e);
                }
            }
        }
    }
}
