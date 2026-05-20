package com.sjp.recruitment.service;

import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.repository.ApplicationRepository;
import com.sjp.recruitment.repository.CandidateProfileRepository;
import com.sjp.recruitment.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final CandidateProfileRepository candidateRepository;

    @Transactional(readOnly = true)
    public Page<Application> findByCandidateId(Long candidateId, Pageable pageable) {
        return applicationRepository.findByCandidateId(candidateId, pageable);
    }

    @Transactional
    public Application apply(Long candidateId, Long jobId) {
        if (applicationRepository.existsByCandidateIdAndJobId(candidateId, jobId)) {
            throw new RuntimeException("Already applied for this job");
        }

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        CandidateProfile candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));

        Application application = new Application();
        application.setJob(job);
        application.setCandidate(candidate);
        application.setStatus(Application.ApplicationStatus.PENDING);

        return applicationRepository.save(application);
    }
}