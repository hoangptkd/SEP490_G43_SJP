package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.InterviewAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, UUID> {
    List<InterviewAnswer> findBySessionIdOrderByAnsweredAtAsc(UUID sessionId);
    Optional<InterviewAnswer> findBySessionIdAndQuestionId(UUID sessionId, UUID questionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select answer from InterviewAnswer answer "
            + "where answer.session.id = :sessionId and answer.questionId = :questionId")
    Optional<InterviewAnswer> findBySessionIdAndQuestionIdForUpdate(
            @Param("sessionId") UUID sessionId,
            @Param("questionId") UUID questionId);
    boolean existsBySessionIdAndQuestionId(UUID sessionId, UUID questionId);
    long countBySessionId(UUID sessionId);
    List<InterviewAnswer> findBySessionIdInOrderBySessionIdAscAnsweredAtAsc(Collection<UUID> sessionIds);
}
