package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.AiQuestionBank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiQuestionBankRepository extends JpaRepository<AiQuestionBank, UUID> {
    List<AiQuestionBank> findByQuestionSet_IdAndActiveTrueOrderByOrderIndexAsc(UUID questionSetId);
    long countByQuestionSet_IdAndActiveTrue(UUID questionSetId);
}
