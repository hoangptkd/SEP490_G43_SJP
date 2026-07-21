package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.CompanyIndustry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyIndustryRepository extends JpaRepository<CompanyIndustry, UUID> {
    List<CompanyIndustry> findByCompanyIdOrderByPrimaryDescCreatedAtDesc(UUID companyId);
    Optional<CompanyIndustry> findByCompanyIdAndPrimaryTrue(UUID companyId);
    List<CompanyIndustry> findByCompanyId(UUID companyId);
    void deleteByCompanyId(UUID companyId);
}
