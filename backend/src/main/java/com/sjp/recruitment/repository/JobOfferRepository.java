package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.JobOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface JobOfferRepository extends JpaRepository<JobOffer, UUID> {

    Optional<JobOffer> findByApplicationId(UUID applicationId);

    List<JobOffer> findByApplicationIdIn(List<UUID> applicationIds);

    Optional<JobOffer> findByIdAndEmployerId(UUID id, UUID employerId);

    java.util.List<JobOffer> findByApplicationJobEmployerIdAndStatus(UUID employerId, String status);

    org.springframework.data.domain.Page<JobOffer> findByApplicationJobEmployerIdAndStatusOrderByCreatedAtDesc(UUID employerId, String status, org.springframework.data.domain.Pageable pageable);

    boolean existsByApplicationId(UUID applicationId);
}
