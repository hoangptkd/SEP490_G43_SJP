package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {
    Optional<CandidateProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);

    @Query("SELECT cp FROM CandidateProfile cp JOIN cp.user u WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<CandidateProfile> searchByName(String name);

    @Query(value = """
            SELECT js.*
            FROM job_seekers js
            JOIN candidate_skills cs ON cs.job_seeker_id = js.id
            JOIN skills s ON s.id = cs.skill_id
            WHERE LOWER(s.name) = LOWER(:skill)
            """, nativeQuery = true)
    List<CandidateProfile> findBySkillContaining(String skill);
}
