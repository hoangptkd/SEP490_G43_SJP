package com.sjp.recruitment.model.dto.response;

public record HandsFreeAnswerCaptureResponse(
        String questionId,
        String captureId,
        int captureVersion,
        String browserTranscript,
        String gladiaTranscript,
        String rawTranscript,
        String correctedTranscript,
        String correctionStatus,
        int correctionCount,
        String transcriptStatus,
        String dataQuality,
        HandsFreeVadMetricsResponse vadMetrics
) {}
