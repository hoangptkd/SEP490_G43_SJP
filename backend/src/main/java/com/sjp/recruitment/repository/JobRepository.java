package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {
    Page<Job> findByStatus(Job.JobStatus status, Pageable pageable);
    Page<Job> findByEmployerId(Long employerId, Pageable pageable);

    @Query("SELECT j FROM Job j WHERE " +
           "LOWER(j.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(j.description) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Job> searchByKeyword(@Param("search") String search, Pageable pageable);

    @Query("SELECT j FROM Job j WHERE j.location LIKE LOWER(CONCAT('%', :location, '%'))")
    Page<Job> findByLocation(@Param("location") String location, Pageable pageable);

    List<Job> findByStatusAndSalaryMinGreaterThan(Job.JobStatus status, Integer minSalary);
}