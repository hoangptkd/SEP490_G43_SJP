package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.JobAlertRequest;
import com.sjp.recruitment.model.dto.response.JobAlertResponse;
import com.sjp.recruitment.model.dto.response.PageResponse;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.model.entity.JobAlert;
import com.sjp.recruitment.model.entity.Notification;
import com.sjp.recruitment.repository.JobAlertRepository;
import com.sjp.recruitment.repository.JobRepository;
import com.sjp.recruitment.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobAlertService {
    private final CandidateService candidateService;
    private final JobAlertRepository jobAlertRepository;
    private final JobRepository jobRepository;
    private final NotificationRepository notificationRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CandidateRealtimeEventPublisher realtimeEventPublisher;

    @Transactional(readOnly = true)
    public PageResponse<JobAlertResponse> list(int page, int size) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        return PageResponse.from(jobAlertRepository.findByCandidateId(candidate.getId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "createdAt"))), this::response);
    }

    @Transactional
    public JobAlertResponse create(JobAlertRequest request) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        JobAlert alert = new JobAlert();
        alert.setCandidate(candidate);
        alert.setLastRunAt(LocalDateTime.now());
        apply(alert, request);
        return response(jobAlertRepository.save(alert));
    }

    @Transactional
    public JobAlertResponse update(String id, JobAlertRequest request) {
        JobAlert alert = owned(id);
        apply(alert, request);
        return response(alert);
    }

    @Transactional
    public void delete(String id) {
        jobAlertRepository.delete(owned(id));
    }

    @Transactional
    public void processDueAlerts() {
        LocalDateTime now = LocalDateTime.now();
        for (JobAlert alert : jobAlertRepository.findByEnabledTrue()) {
            long hours = "WEEKLY".equals(alert.getFrequency()) ? 168 : 24;
            if (alert.getLastRunAt() != null && alert.getLastRunAt().plusHours(hours).isAfter(now)) continue;
            LocalDateTime since = alert.getLastRunAt() == null ? now.minusHours(hours) : alert.getLastRunAt();
            findMatches(alert, since).forEach(job -> notifyOnce(alert, job));
            alert.setLastRunAt(now);
        }
    }

    private List<Job> findMatches(JobAlert alert, LocalDateTime since) {
        String sql = """
                SELECT j.id FROM jobs j
                LEFT JOIN categories c ON c.id = j.category_id
                WHERE j.status = 'published'
                  AND (j.deadline IS NULL OR j.deadline >= CURRENT_DATE)
                  AND COALESCE(j.published_at, j.created_at) > :since
                  AND (:keyword = '' OR j.title ILIKE :keywordLike OR j.description ILIKE :keywordLike)
                  AND (:location = '' OR j.location ILIKE :locationLike)
                  AND (:category = '' OR LOWER(COALESCE(c.slug, c.name, '')) = LOWER(:category))
                  AND (:jobType = '' OR j.job_type = :jobType)
                  AND (:workMode = '' OR j.work_mode = :workMode)
                  AND (:minSalary IS NULL OR j.salary_max IS NULL OR j.salary_max >= :minSalary)
                  AND (:maxSalary IS NULL OR j.salary_min IS NULL OR j.salary_min <= :maxSalary)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("since", since)
                .addValue("keyword", text(alert.getKeyword()))
                .addValue("keywordLike", "%" + text(alert.getKeyword()) + "%")
                .addValue("location", text(alert.getLocation()))
                .addValue("locationLike", "%" + text(alert.getLocation()) + "%")
                .addValue("category", text(alert.getCategory()))
                .addValue("jobType", text(alert.getJobType()))
                .addValue("workMode", text(alert.getWorkMode()))
                .addValue("minSalary", alert.getMinSalary())
                .addValue("maxSalary", alert.getMaxSalary());
        List<UUID> ids = jdbcTemplate.query(sql, params, (rs, row) -> UUID.fromString(rs.getString(1)));
        return jobRepository.findAllById(ids);
    }

    private void notifyOnce(JobAlert alert, Job job) {
        try {
            int inserted = jdbcTemplate.update("""
                    INSERT INTO job_alert_matches (alert_id, job_id, created_at)
                    VALUES (:alertId, :jobId, now()) ON CONFLICT DO NOTHING
                    """, new MapSqlParameterSource().addValue("alertId", alert.getId()).addValue("jobId", job.getId()));
            if (inserted == 0) return;
            Notification notification = new Notification();
            notification.setRecipientUser(alert.getCandidate().getUser());
            notification.setType("JOB_ALERT_MATCH");
            notification.setTitle("Việc làm mới phù hợp với cảnh báo");
            notification.setMessage(job.getTitle() + " phù hợp với cảnh báo “" + alert.getName() + "”.");
            notification.setRelatedEntityType("JOB");
            notification.setRelatedEntityId(job.getId());
            notification.setLinkUrl("/jobs/" + job.getId());
            Notification saved = notificationRepository.save(notification);
            realtimeEventPublisher.publishAfterCommit(
                    alert.getCandidate().getUser(), "NOTIFICATION_UPDATED", saved.getId());
        } catch (DataIntegrityViolationException ignored) {
            // Another scheduler instance already created this alert/job match.
        }
    }

    private JobAlert owned(String id) {
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        try {
            return jobAlertRepository.findByIdAndCandidateId(UUID.fromString(id), candidate.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_ALERT_NOT_FOUND", "Không tìm thấy cảnh báo việc làm"));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "JOB_ALERT_ID_INVALID", "Mã cảnh báo không hợp lệ");
        }
    }

    private void apply(JobAlert alert, JobAlertRequest request) {
        if (request.minSalary() != null && request.maxSalary() != null && request.minSalary().compareTo(request.maxSalary()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SALARY_RANGE_INVALID", "Mức lương tối thiểu không thể lớn hơn mức tối đa");
        }
        alert.setName(request.name().trim());
        alert.setKeyword(text(request.keyword())); alert.setLocation(text(request.location())); alert.setCategory(text(request.category()));
        alert.setJobType(text(request.jobType())); alert.setWorkMode(text(request.workMode()));
        alert.setMinSalary(request.minSalary()); alert.setMaxSalary(request.maxSalary());
        alert.setFrequency(request.frequency() == null ? "DAILY" : request.frequency());
        alert.setEnabled(request.enabled() == null || request.enabled());
    }

    private JobAlertResponse response(JobAlert a) {
        return new JobAlertResponse(a.getId().toString(), a.getName(), a.getKeyword(), a.getLocation(), a.getCategory(), a.getJobType(),
                a.getWorkMode(), a.getMinSalary(), a.getMaxSalary(), a.getFrequency(), a.isEnabled(), a.getLastRunAt(), a.getCreatedAt());
    }

    private String text(String value) { return value == null ? "" : value.trim(); }
}
