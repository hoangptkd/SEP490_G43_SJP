package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.CandidateCv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateCvRepository extends JpaRepository<CandidateCv, UUID> {
    List<CandidateCv> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);
    List<CandidateCv> findByCandidateIdAndSourceTypeAndDeletedAtIsNullOrderByCreatedAtDesc(UUID candidateId, String sourceType);
    Optional<CandidateCv> findByIdAndCandidateId(UUID id, UUID candidateId);
    Optional<CandidateCv> findByIdAndCandidateIdAndSourceTypeAndDeletedAtIsNull(UUID id, UUID candidateId, String sourceType);
    boolean existsByCandidateIdAndSourceTypeAndDeletedAtIsNull(UUID candidateId, String sourceType);
    boolean existsByCandidateIdAndDeletedAtIsNull(UUID candidateId);
}
