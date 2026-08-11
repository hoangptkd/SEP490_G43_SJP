package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.CandidateAiConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CandidateAiConsentRepository extends JpaRepository<CandidateAiConsent, UUID> {
    Optional<CandidateAiConsent> findFirstByCandidateIdAndPurposeAndRevokedAtIsNullOrderByGrantedAtDesc(
            UUID candidateId, String purpose);
}
