package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.CompanyDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyDocumentRepository extends JpaRepository<CompanyDocument, UUID> {
    List<CompanyDocument> findByCompanyIdOrderByUploadedAtDesc(UUID companyId);

    Optional<CompanyDocument> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyId(UUID companyId);

    long countByCompanyIdAndStatusIgnoreCase(UUID companyId, String status);

    List<CompanyDocument> findByCompanyIdAndStatusIgnoreCase(UUID companyId, String status);
}
