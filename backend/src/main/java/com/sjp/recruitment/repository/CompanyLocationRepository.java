package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.CompanyLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyLocationRepository extends JpaRepository<CompanyLocation, UUID> {
    List<CompanyLocation> findByCompanyIdOrderByHeadquarterDescCreatedAtDesc(UUID companyId);
    Optional<CompanyLocation> findByIdAndCompanyId(UUID id, UUID companyId);
    Optional<CompanyLocation> findByCompanyIdAndHeadquarterTrue(UUID companyId);
    List<CompanyLocation> findByCompanyId(UUID companyId);
}
