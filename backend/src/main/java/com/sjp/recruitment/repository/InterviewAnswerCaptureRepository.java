package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.InterviewAnswerCapture;
import com.sjp.recruitment.model.enums.VoiceEvidenceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface InterviewAnswerCaptureRepository extends JpaRepository<InterviewAnswerCapture, UUID> {
    Optional<InterviewAnswerCapture> findByAnswerIdAndCaptureIdAndCaptureVersion(
            UUID answerId, UUID captureId, int captureVersion);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select capture from InterviewAnswerCapture capture "
            + "where capture.answer.id = :answerId and capture.captureId = :captureId "
            + "and capture.captureVersion = :captureVersion")
    Optional<InterviewAnswerCapture> findForUpdate(
            @Param("answerId") UUID answerId,
            @Param("captureId") UUID captureId,
            @Param("captureVersion") int captureVersion);

    Optional<InterviewAnswerCapture> findTopByAnswerIdAndCaptureIdOrderByCaptureVersionDesc(
            UUID answerId, UUID captureId);

    boolean existsByCaptureIdAndAnswerIdNot(UUID captureId, UUID answerId);

    @EntityGraph(attributePaths = {"answer", "conversationTurn"})
    List<InterviewAnswerCapture> findByAnswerIdIn(List<UUID> answerIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"answer", "conversationTurn"})
    @Query("select capture from InterviewAnswerCapture capture "
            + "where capture.answer.id in :answerIds")
    List<InterviewAnswerCapture> findByAnswerIdInForUpdate(
            @Param("answerIds") List<UUID> answerIds);

    long countByAnswerIdInAndVoiceEvidenceStatus(List<UUID> answerIds, VoiceEvidenceStatus status);
}
