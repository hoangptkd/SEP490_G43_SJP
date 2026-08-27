package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.CandidateCv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateCvRepository extends JpaRepository<CandidateCv, UUID> {
    List<CandidateCv> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);
    List<CandidateCv> findByCandidateIdAndSourceTypeAndDeletedAtIsNullOrderByCreatedAtDesc(UUID candidateId, String sourceType);
    Page<CandidateCv> findByCandidateIdAndSourceTypeAndDeletedAtIsNull(UUID candidateId, String sourceType, Pageable pageable);
    Optional<CandidateCv> findByIdAndCandidateId(UUID id, UUID candidateId);
    Optional<CandidateCv> findByIdAndCandidateIdAndSourceTypeAndDeletedAtIsNull(UUID id, UUID candidateId, String sourceType);
    boolean existsByCandidateIdAndSourceTypeAndDeletedAtIsNull(UUID candidateId, String sourceType);
    boolean existsByCandidateIdAndDeletedAtIsNull(UUID candidateId);
    Optional<CandidateCv> findFirstByCandidateIdAndDefaultCvTrueAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID candidateId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE CandidateCv cv SET cv.defaultCv = false WHERE cv.candidate.id = :candidateId AND cv.defaultCv = true")
    int clearDefaultForCandidate(@Param("candidateId") UUID candidateId);
}
