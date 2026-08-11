package com.sjp.recruitment.model.dto.response;

import com.sjp.recruitment.service.vad.VadAnalysisResult;

import java.util.List;

public record HandsFreeVadMetricsResponse(
        List<SpeechSegmentResponse> speechSegments,
        double speakingDurationSeconds,
        Double speechOnsetSeconds,
        int internalPauseCount,
        double longestInternalPauseSeconds,
        double totalInternalPauseDurationSeconds,
        double pauseDurationRatio,
        String modelVersion,
        String status
) {
    public static HandsFreeVadMetricsResponse from(VadAnalysisResult result) {
        if (result == null || !result.completed()) return null;
        return new HandsFreeVadMetricsResponse(
                result.speechSegments().stream()
                        .map(segment -> new SpeechSegmentResponse(segment.startMs(), segment.endMs()))
                        .toList(),
                result.speakingDurationSeconds(), result.speechOnsetSeconds(), result.internalPauseCount(),
                result.longestInternalPauseSeconds(), result.totalInternalPauseDurationSeconds(),
                result.pauseDurationRatio(), result.modelVersion(), "completed"
        );
    }

    public record SpeechSegmentResponse(long startMs, long endMs) {}
}
