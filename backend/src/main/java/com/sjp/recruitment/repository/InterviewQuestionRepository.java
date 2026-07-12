package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, UUID> {
    List<InterviewQuestion> findBySessionIdOrderByOrderIndexAsc(UUID sessionId);
    Optional<InterviewQuestion> findTopBySessionIdOrderByOrderIndexDesc(UUID sessionId);
    Optional<InterviewQuestion> findByIdAndSessionId(UUID id, UUID sessionId);
    long countBySessionId(UUID sessionId);
    List<InterviewQuestion> findBySessionIdInOrderBySessionIdAscOrderIndexAsc(Collection<UUID> sessionIds);
}
