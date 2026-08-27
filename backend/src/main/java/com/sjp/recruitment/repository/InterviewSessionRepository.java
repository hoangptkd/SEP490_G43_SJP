package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {
    @EntityGraph(attributePaths = {
            "candidate",
            "job",
            "job.company",
            "job.employer",
            "job.employer.user",
            "job.companyLocation",
            "job.jobSkills",
            "job.jobSkills.skill",
            "application"
    })
    List<InterviewSession> findByCandidateIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID candidateId);
    @Query("select session.id from InterviewSession session "
            + "where session.candidate.id = :candidateId and session.deletedAt is null "
            + "order by session.updatedAt desc")
    List<UUID> findIdsByCandidateIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            @Param("candidateId") UUID candidateId,
            Pageable pageable);

    @EntityGraph(attributePaths = {
            "candidate",
            "job",
            "job.company",
            "job.employer",
            "job.employer.user",
            "job.companyLocation",
            "job.jobSkills",
            "job.jobSkills.skill",
            "application"
    })
    @Query("select distinct session from InterviewSession session where session.id in :ids")
    List<InterviewSession> findAllWithResponseDetailsByIdIn(@Param("ids") List<UUID> ids);
    @EntityGraph(attributePaths = {
            "candidate",
            "job",
            "job.company",
            "job.employer",
            "job.employer.user",
            "job.companyLocation",
            "job.jobSkills",
            "job.jobSkills.skill",
            "application"
    })
    Optional<InterviewSession> findByIdAndCandidateIdAndDeletedAtIsNull(UUID id, UUID candidateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from InterviewSession session where session.id = :id and session.candidate.id = :candidateId and session.deletedAt is null")
    Optional<InterviewSession> findOwnedForUpdate(@Param("id") UUID id, @Param("candidateId") UUID candidateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from InterviewSession session where session.id = :id and session.deletedAt is null")
    Optional<InterviewSession> findByIdForUpdate(@Param("id") UUID id);
}
