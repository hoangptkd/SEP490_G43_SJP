package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewAnswerCapture;
import com.sjp.recruitment.model.entity.InterviewConversationTurn;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.enums.VoiceEvidenceStatus;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiInterviewScoreCalculatorTest {

    private AiInterviewProperties properties;
    private AiInterviewScoreCalculator calculator;
    private InterviewQuestion question;
    private InterviewQuestion skippedQuestionTwo;
    private InterviewQuestion skippedQuestionThree;
    private InterviewAnswer answer;
    private InterviewAnswer skippedAnswerTwo;
    private InterviewAnswer skippedAnswerThree;
    private Map<String, Object> evaluationProfile;

    @BeforeEach
    void setUp() {
        properties = new AiInterviewProperties();
        properties.setPauseFrequencyU1(25);
        properties.setPauseFrequencyU0(40);
        calculator = new AiInterviewScoreCalculator(properties);

        question = new InterviewQuestion();
        question.setId(UUID.randomUUID());
        question.setCompetencyId("communication");
        skippedQuestionTwo = question("technical-depth");
        skippedQuestionThree = question("problem-solving");

        answer = new InterviewAnswer();
        answer.setQuestionId(question.getId());
        answer.setAnsweredAt(LocalDateTime.now());
        answer.setTranscriptText("Tôi đã chủ động trao đổi rõ ràng với cả nhóm");
        answer.setSpeechAnalysisJson(Map.of(
                "dataQuality", "AUDIO_VAD_PLUS_GLADIA",
                "metrics", Map.of(
                        "status", "completed",
                        "speakingDurationSeconds", 2.0,
                        "speechSegments", List.of(
                                Map.of("startMs", 0, "endMs", 1000),
                                Map.of("startMs", 2000, "endMs", 3000)),
                        "totalInternalPauseDurationSeconds", 1.0,
                        "internalPauseCount", 1,
                        "longestInternalPauseSeconds", 1.0
                )
        ));
        skippedAnswerTwo = skippedAnswer(skippedQuestionTwo);
        skippedAnswerThree = skippedAnswer(skippedQuestionThree);
        evaluationProfile = Map.of(
                "voiceWeight", 0.15,
                "scoredCompetencyIds", List.of("communication", "technical-depth", "problem-solving"),
                "competencies", List.of(
                        Map.of("id", "communication", "scoredWeight", 0.5),
                        Map.of("id", "technical-depth", "scoredWeight", 0.3),
                        Map.of("id", "problem-solving", "scoredWeight", 0.2),
                        Map.of("id", "leadership", "scoredWeight", 0.0)
                )
        );
    }

    @Test
    void calculatesDeterministicContentVoiceAndOverallScores() {
        ShopAiKeyClient.QuestionRatingDraft rating = new ShopAiKeyClient.QuestionRatingDraft(
                question.getId().toString(), "communication", 4,
                List.of("Có hành động cụ thể"), List.of());

        AiInterviewScoreCalculator.ScoreResult result = calculator.calculate(
                evaluationProfile,
                List.of(question, skippedQuestionTwo, skippedQuestionThree),
                List.of(answer, skippedAnswerTwo, skippedAnswerThree),
                List.of(rating));

        assertEquals(new BigDecimal("37.50"), result.contentScore());
        assertEquals(new BigDecimal("100.00"), result.voiceDeliveryScore());
        assertEquals(new BigDecimal("100.00"), result.rawVoiceDeliveryScore());
        assertEquals(new BigDecimal("0.00"), result.replayPenalty());
        assertEquals(new BigDecimal("46.88"), result.overallScore());
        assertEquals(new BigDecimal("75.00"), result.questionResults().get(question.getId()).questionScore());
    }

    @Test
    void completesWithContentOnlyWhenVoiceEvidenceIsUnavailable() {
        answer.setSpeechAnalysisJson(Map.of("dataQuality", "BROWSER_TRANSCRIPT_ONLY"));
        ShopAiKeyClient.QuestionRatingDraft rating = new ShopAiKeyClient.QuestionRatingDraft(
                question.getId().toString(), "communication", 3, List.of(), List.of());

        AiInterviewScoreCalculator.ScoreResult result = calculator.calculate(
                evaluationProfile,
                List.of(question, skippedQuestionTwo, skippedQuestionThree),
                List.of(answer, skippedAnswerTwo, skippedAnswerThree),
                List.of(rating));

        assertEquals(new BigDecimal("25.00"), result.contentScore());
        assertEquals(null, result.voiceDeliveryScore());
        assertEquals(null, result.rawVoiceDeliveryScore());
        assertEquals(new BigDecimal("0.00"), result.voiceWeight());
        assertEquals(new BigDecimal("25.00"), result.overallScore());
        assertEquals(0, result.voiceEvidenceQuestionCount());
        assertEquals(1, result.manualFallbackQuestionCount());
    }

    @Test
    void appliesReplayPenaltyOnlyToVoiceDelivery() {
        question.setReplayCount(2);
        ShopAiKeyClient.QuestionRatingDraft rating = new ShopAiKeyClient.QuestionRatingDraft(
                question.getId().toString(), "communication", 4, List.of(), List.of());

        AiInterviewScoreCalculator.ScoreResult result = calculator.calculate(
                evaluationProfile,
                List.of(question, skippedQuestionTwo, skippedQuestionThree),
                List.of(answer, skippedAnswerTwo, skippedAnswerThree),
                List.of(rating));

        assertEquals(new BigDecimal("37.50"), result.contentScore());
        assertEquals(new BigDecimal("100.00"), result.rawVoiceDeliveryScore());
        assertEquals(new BigDecimal("96.00"), result.voiceDeliveryScore());
        assertEquals(new BigDecimal("4.00"), result.replayPenalty());
        assertEquals(2, result.replayCount());
        assertEquals(new BigDecimal("46.28"), result.overallScore());
    }

    @Test
    void capsReplayPenaltyAtTenVoicePoints() {
        question.setReplayCount(7);
        ShopAiKeyClient.QuestionRatingDraft rating = new ShopAiKeyClient.QuestionRatingDraft(
                question.getId().toString(), "communication", 4, List.of(), List.of());

        AiInterviewScoreCalculator.ScoreResult result = calculator.calculate(
                evaluationProfile,
                List.of(question, skippedQuestionTwo, skippedQuestionThree),
                List.of(answer, skippedAnswerTwo, skippedAnswerThree),
                List.of(rating));

        assertEquals(new BigDecimal("37.50"), result.contentScore());
        assertEquals(new BigDecimal("100.00"), result.rawVoiceDeliveryScore());
        assertEquals(new BigDecimal("90.00"), result.voiceDeliveryScore());
        assertEquals(new BigDecimal("10.00"), result.replayPenalty());
        assertEquals(7, result.replayCount());
        assertEquals(new BigDecimal("45.38"), result.overallScore());
    }

    @Test
    void trapezoidBandUsesPlateauAndLinearEdges() {
        assertEquals(0.0, calculator.band(0, 0, 10, 20, 30));
        assertEquals(50.0, calculator.band(5, 0, 10, 20, 30));
        assertEquals(100.0, calculator.band(15, 0, 10, 20, 30));
        assertEquals(50.0, calculator.band(25, 0, 10, 20, 30));
        assertEquals(0.0, calculator.band(30, 0, 10, 20, 30));
    }

    @Test
    void keepsRatedBarsOneDistinctFromSkippedNotAnswered() {
        ShopAiKeyClient.QuestionRatingDraft rating = new ShopAiKeyClient.QuestionRatingDraft(
                question.getId().toString(), "communication", 1, List.of(), List.of("Thiếu bằng chứng"));

        AiInterviewScoreCalculator.ScoreResult result = calculator.calculate(
                evaluationProfile,
                List.of(question, skippedQuestionTwo, skippedQuestionThree),
                List.of(answer, skippedAnswerTwo, skippedAnswerThree),
                List.of(rating));

        AiInterviewScoreCalculator.QuestionScoreResult rated = result.questionResults().get(question.getId());
        assertEquals("RATED", rated.evaluationStatus());
        assertEquals(1, rated.barsLevel());
        assertEquals(new BigDecimal("0.00"), rated.questionScore());
        assertEquals(null, rated.scoreReason());

        AiInterviewScoreCalculator.QuestionScoreResult skipped =
                result.questionResults().get(skippedQuestionTwo.getId());
        assertEquals("NOT_ANSWERED", skipped.evaluationStatus());
        assertEquals(null, skipped.barsLevel());
        assertEquals(new BigDecimal("0.00"), skipped.questionScore());
        assertEquals("SKIPPED", skipped.scoreReason());
    }

    @Test
    void transcriptEditingDoesNotChangeVoiceScoreWhenCaptureEvidenceExists() {
        answer.setId(UUID.randomUUID());
        answer.setFinalTranscript("Bản transcript đã được người dùng sửa rất dài và rõ ràng hơn.");
        InterviewConversationTurn turn = new InterviewConversationTurn();
        turn.setId(UUID.randomUUID());
        InterviewAnswerCapture capture = new InterviewAnswerCapture();
        capture.setAnswer(answer);
        capture.setConversationTurn(turn);
        capture.setCaptureVersion(1);
        capture.setStatus("completed");
        capture.setVoiceEvidenceStatus(VoiceEvidenceStatus.COMPLETED);
        capture.setDataQuality("AUDIO_VAD_PLUS_GLADIA");
        capture.setGladiaTranscript("Tôi trao đổi với nhóm");
        capture.setVadMetricsJson(Map.of(
                "status", "completed",
                "speakingDurationSeconds", 2.0,
                "speechSegments", List.of(
                        Map.of("startMs", 0, "endMs", 1000),
                        Map.of("startMs", 2000, "endMs", 3000)),
                "totalInternalPauseDurationSeconds", 1.0,
                "internalPauseCount", 1,
                "longestInternalPauseSeconds", 1.0
        ));
        ShopAiKeyClient.QuestionRatingDraft rating = new ShopAiKeyClient.QuestionRatingDraft(
                question.getId().toString(), "communication", 4, List.of(), List.of());

        AiInterviewScoreCalculator.ScoreResult before = calculator.calculate(
                evaluationProfile,
                List.of(question, skippedQuestionTwo, skippedQuestionThree),
                List.of(answer, skippedAnswerTwo, skippedAnswerThree),
                List.of(rating),
                List.of(capture));
        answer.setFinalTranscript("Nội dung chỉnh sửa hoàn toàn khác và dài hơn rất nhiều lần.");
        answer.setTranscriptText(answer.getFinalTranscript());
        AiInterviewScoreCalculator.ScoreResult after = calculator.calculate(
                evaluationProfile,
                List.of(question, skippedQuestionTwo, skippedQuestionThree),
                List.of(answer, skippedAnswerTwo, skippedAnswerThree),
                List.of(rating),
                List.of(capture));

        assertEquals(before.rawVoiceDeliveryScore(), after.rawVoiceDeliveryScore());
        assertEquals(before.voiceDeliveryScore(), after.voiceDeliveryScore());
    }

    private InterviewQuestion question(String competencyId) {
        InterviewQuestion value = new InterviewQuestion();
        value.setId(UUID.randomUUID());
        value.setCompetencyId(competencyId);
        return value;
    }

    private InterviewAnswer skippedAnswer(InterviewQuestion value) {
        InterviewAnswer skipped = new InterviewAnswer();
        skipped.setQuestionId(value.getId());
        skipped.setAnsweredAt(LocalDateTime.now());
        skipped.setSkipped(true);
        skipped.setTranscriptText("[SKIPPED]");
        return skipped;
    }
}
