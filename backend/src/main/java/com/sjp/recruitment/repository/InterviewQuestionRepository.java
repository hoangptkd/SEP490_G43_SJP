package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update InterviewQuestion question set question.replayCount = question.replayCount + 1 "
            + "where question.id = :questionId and question.session.id = :sessionId")
    int incrementReplayCount(@Param("questionId") UUID questionId, @Param("sessionId") UUID sessionId);
}
