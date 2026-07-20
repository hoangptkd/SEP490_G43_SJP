package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.JobSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobSkillRepository extends JpaRepository<JobSkill, UUID> {
    List<JobSkill> findByJobId(UUID jobId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM JobSkill js WHERE js.job.id = :jobId")
    void deleteByJobId(@Param("jobId") UUID jobId);
}
