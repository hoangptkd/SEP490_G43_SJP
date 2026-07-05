package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.Employer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployerRepository extends JpaRepository<Employer, UUID> {
    Optional<Employer> findByUserId(UUID userId);

    List<Employer> findByCompanyId(UUID companyId);

    @Query("SELECT e FROM Employer e JOIN FETCH e.user WHERE e.company.id = :companyId AND e.owner = true")
    Optional<Employer> findOwnerByCompanyId(@Param("companyId") UUID companyId);

    @Query("SELECT e FROM Employer e JOIN FETCH e.user WHERE e.company.id = :companyId ORDER BY e.owner DESC, e.createdAt ASC")
    List<Employer> findByCompanyIdWithUser(@Param("companyId") UUID companyId);
}
