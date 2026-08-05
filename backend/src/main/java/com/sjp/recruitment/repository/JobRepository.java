package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {
    Page<Job> findByStatus(String status, Pageable pageable);
    Page<Job> findByEmployerId(UUID employerId, Pageable pageable);
    List<Job> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    List<Job> findByEmployerIdOrderByCreatedAtDesc(UUID employerId);
    
    long countByEmployerId(UUID employerId);
    long countByEmployerIdAndStatus(UUID employerId, String status);

    List<Job> findByStatusIgnoreCaseOrderByUpdatedAtDesc(String status);

    @Query("""
            SELECT j FROM Job j
            JOIN FETCH j.company
            JOIN FETCH j.employer e
            JOIN FETCH e.user
            WHERE LOWER(j.status) = LOWER(:status)
            ORDER BY j.updatedAt DESC
            """)
    List<Job> findByStatusWithDetails(@Param("status") String status);

    @Query("""
            SELECT j FROM Job j
            JOIN FETCH j.company
            JOIN FETCH j.employer e
            JOIN FETCH e.user
            WHERE j.id = :id
            """)
    Optional<Job> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
           SELECT j FROM Job j
           WHERE j.status = 'published'
             AND (:search IS NULL OR :search = '' OR
                  LOWER(j.title) LIKE LOWER(CONCAT('%', :search, '%')) OR
                  LOWER(j.description) LIKE LOWER(CONCAT('%', :search, '%')))
             AND (:location IS NULL OR :location = '' OR LOWER(j.location) LIKE LOWER(CONCAT('%', :location, '%')))
             AND (:minSalary IS NULL OR j.salaryMax >= :minSalary)
             AND (:maxSalary IS NULL OR j.salaryMin <= :maxSalary)
             AND (:experienceLevel IS NULL OR :experienceLevel = '' OR j.experienceLevel = :experienceLevel)
             AND (:now IS NULL OR j.deadline IS NULL OR j.deadline >= :now)
           """)
    Page<Job> searchJobs(
            @Param("search") String search,
            @Param("location") String location,
            @Param("minSalary") BigDecimal minSalary,
            @Param("maxSalary") BigDecimal maxSalary,
            @Param("experienceLevel") String experienceLevel,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query("SELECT j FROM Job j WHERE LOWER(j.location) LIKE LOWER(CONCAT('%', :location, '%'))")
    Page<Job> findByLocation(@Param("location") String location, Pageable pageable);

    List<Job> findTop20ByStatusOrderByCreatedAtDesc(String status);
    List<Job> findByStatusAndSalaryMinGreaterThan(String status, BigDecimal minSalary);

    @Query("SELECT j FROM Job j WHERE j.status = 'published' AND j.deadline < :today")
    List<Job> findExpiredPublishedJobs(@Param("today") LocalDate today);
}
