package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.CvVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CvVersionRepository extends JpaRepository<CvVersion, UUID> {
    List<CvVersion> findByCandidateIdOrderByUpdatedAtDesc(UUID candidateId);
    Optional<CvVersion> findByIdAndCandidateId(UUID id, UUID candidateId);
}
