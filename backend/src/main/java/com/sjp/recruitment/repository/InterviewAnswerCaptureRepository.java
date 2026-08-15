package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.InterviewAnswerCapture;
import com.sjp.recruitment.model.enums.VoiceEvidenceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface InterviewAnswerCaptureRepository extends JpaRepository<InterviewAnswerCapture, UUID> {
    Optional<InterviewAnswerCapture> findByAnswerIdAndCaptureIdAndCaptureVersion(
            UUID answerId, UUID captureId, int captureVersion);

    Optional<InterviewAnswerCapture> findTopByAnswerIdAndCaptureIdOrderByCaptureVersionDesc(
            UUID answerId, UUID captureId);

    boolean existsByCaptureIdAndAnswerIdNot(UUID captureId, UUID answerId);

    @EntityGraph(attributePaths = {"answer", "conversationTurn"})
    List<InterviewAnswerCapture> findByAnswerIdIn(List<UUID> answerIds);

    long countByAnswerIdInAndVoiceEvidenceStatus(List<UUID> answerIds, VoiceEvidenceStatus status);
}
