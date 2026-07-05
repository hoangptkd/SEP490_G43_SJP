package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {
    Optional<Company> findByName(String name);

    List<Company> findByVerificationStatusIgnoreCaseOrderByUpdatedAtDesc(String verificationStatus);

    List<Company> findAllByOrderByUpdatedAtDesc();
}
