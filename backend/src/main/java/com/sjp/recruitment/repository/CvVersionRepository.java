package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.CvVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CvVersionRepository extends JpaRepository<CvVersion, UUID> {
    List<CvVersion> findByCandidateIdAndSourceTypeAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID candidateId, String sourceType);
    Page<CvVersion> findByCandidateIdAndSourceTypeAndDeletedAtIsNull(UUID candidateId, String sourceType, Pageable pageable);
    Optional<CvVersion> findByIdAndCandidateId(UUID id, UUID candidateId);
    Optional<CvVersion> findByIdAndCandidateIdAndSourceTypeAndDeletedAtIsNull(UUID id, UUID candidateId, String sourceType);
}
