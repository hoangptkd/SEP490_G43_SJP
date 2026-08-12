package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.JobOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobOfferRepository extends JpaRepository<JobOffer, UUID> {

    Optional<JobOffer> findByApplicationId(UUID applicationId);

    Optional<JobOffer> findByIdAndEmployerId(UUID id, UUID employerId);

    java.util.List<JobOffer> findByApplicationJobEmployerIdAndStatus(UUID employerId, String status);

    boolean existsByApplicationId(UUID applicationId);
}
