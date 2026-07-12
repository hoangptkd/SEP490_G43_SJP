package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.AiAnswerFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface AiAnswerFeedbackRepository extends JpaRepository<AiAnswerFeedback, UUID> {
    Optional<AiAnswerFeedback> findByAnswerId(UUID answerId);
    List<AiAnswerFeedback> findByAnswerIdIn(Collection<UUID> answerIds);
}
