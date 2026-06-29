package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.CandidateSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CandidateSkillRepository extends JpaRepository<CandidateSkill, UUID> {
    void deleteByCandidateId(UUID candidateId);
}
