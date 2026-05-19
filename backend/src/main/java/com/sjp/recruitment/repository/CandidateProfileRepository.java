package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {
    Optional<CandidateProfile> findByUserId(Long userId);
    boolean existsByUserId(Long userId);

    @Query("SELECT cp FROM CandidateProfile cp WHERE LOWER(cp.fullName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<CandidateProfile> searchByName(String name);

    @Query("SELECT cp FROM CandidateProfile cp JOIN cp.skills s WHERE LOWER(s) LIKE LOWER(CONCAT('%', :skill, '%'))")
    List<CandidateProfile> findBySkillContaining(String skill);
}