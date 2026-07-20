package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.AiQuestionSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiQuestionSetRepository extends JpaRepository<AiQuestionSet, UUID> {
    List<AiQuestionSet> findByActiveTrueOrderByTitleAsc();
    Optional<AiQuestionSet> findByIdAndActiveTrue(UUID id);
}
