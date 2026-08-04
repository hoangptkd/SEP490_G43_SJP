package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.CandidateSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CandidateSkillRepository extends JpaRepository<CandidateSkill, UUID> {
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM CandidateSkill cs WHERE cs.candidate.id = :candidateId")
    void deleteByCandidateId(@Param("candidateId") UUID candidateId);
}
