package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.InterviewConversationTurn;
import com.sjp.recruitment.model.enums.InterviewTurnType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

public interface InterviewConversationTurnRepository extends JpaRepository<InterviewConversationTurn, UUID> {
    List<InterviewConversationTurn> findBySessionIdOrderBySequenceNoAsc(UUID sessionId);

    List<InterviewConversationTurn> findBySessionIdInOrderBySessionIdAscSequenceNoAsc(
            Collection<UUID> sessionIds);

    Optional<InterviewConversationTurn> findTopBySessionIdOrderBySequenceNoDesc(UUID sessionId);

    @Query("select turn from InterviewConversationTurn turn "
            + "where turn.session.id = :sessionId and turn.session.currentTurn.id = turn.id")
    Optional<InterviewConversationTurn> findCurrentBySessionId(@Param("sessionId") UUID sessionId);

    Optional<InterviewConversationTurn> findByIdAndSessionId(UUID id, UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select turn from InterviewConversationTurn turn "
            + "where turn.id = :id and turn.session.id = :sessionId")
    Optional<InterviewConversationTurn> findByIdAndSessionIdForUpdate(
            @Param("id") UUID id,
            @Param("sessionId") UUID sessionId);

    List<InterviewConversationTurn> findBySessionIdAndAssessmentItemIdOrderBySequenceNoAsc(
            UUID sessionId, UUID assessmentItemId);

    long countBySessionIdAndTurnType(UUID sessionId, InterviewTurnType turnType);

    Optional<InterviewConversationTurn> findBySessionIdAndAnswerClientId(UUID sessionId, UUID answerClientId);
}
