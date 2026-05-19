package com.sjp.recruitment.service;

import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.model.dto.request.JobRequest;
import com.sjp.recruitment.repository.JobRepository;
import com.sjp.recruitment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<Job> findAll(String search, String location, Pageable pageable) {
        if (search != null && !search.isEmpty()) {
            return jobRepository.searchByKeyword(search, pageable);
        }
        if (location != null && !location.isEmpty()) {
            return jobRepository.findByLocation(location, pageable);
        }
        return jobRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Job findById(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found"));
    }

    @Transactional
    public Job create(JobRequest request) {
        User employer = userRepository.findById(request.getEmployerId())
                .orElseThrow(() -> new RuntimeException("Employer not found"));

        Job job = new Job();
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setRequirements(request.getRequirements());
        job.setSalaryMin(request.getSalaryMin());
        job.setSalaryMax(request.getSalaryMax());
        job.setLocation(request.getLocation());
        job.setStatus(Job.JobStatus.ACTIVE);
        job.setEmployer(employer);

        return jobRepository.save(job);
    }

    @Transactional
    public Job update(Long id, JobRequest request) {
        Job job = findById(id);

        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setRequirements(request.getRequirements());
        job.setSalaryMin(request.getSalaryMin());
        job.setSalaryMax(request.getSalaryMax());
        job.setLocation(request.getLocation());

        return jobRepository.save(job);
    }

    @Transactional
    public void delete(Long id) {
        jobRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Job> findByEmployerId(Long employerId) {
        return jobRepository.findByEmployerId(employerId, org.springframework.data.domain.Pageable.unpaged()).getContent();
    }
}