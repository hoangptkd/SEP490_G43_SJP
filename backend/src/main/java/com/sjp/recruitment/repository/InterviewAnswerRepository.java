package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.InterviewAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, UUID> {
    List<InterviewAnswer> findBySessionIdOrderByAnsweredAtAsc(UUID sessionId);
    Optional<InterviewAnswer> findBySessionIdAndQuestionId(UUID sessionId, UUID questionId);
    boolean existsBySessionIdAndQuestionId(UUID sessionId, UUID questionId);
    long countBySessionId(UUID sessionId);
    List<InterviewAnswer> findBySessionIdInOrderBySessionIdAscAnsweredAtAsc(Collection<UUID> sessionIds);
}
