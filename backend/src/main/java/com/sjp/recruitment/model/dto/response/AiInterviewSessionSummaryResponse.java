package com.sjp.recruitment.model.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AiInterviewSessionSummaryResponse(
        BigDecimal overallScore,
        BigDecimal contentScore,
        BigDecimal voiceDeliveryScore,
        BigDecimal rawVoiceDeliveryScore,
        BigDecimal voiceWeight,
        int replayCount,
        BigDecimal replayPenalty,
        int voiceEvidenceQuestionCount,
        int manualFallbackQuestionCount,
        boolean referenceOnly,
        String summary,
        List<String> strengths,
        List<String> weaknesses,
        List<String> improvementPlan,
        String evaluationProfileVersion,
        String rubricVersion,
        String speechCalibrationVersion,
        String source,
        boolean fallback
) {
}
