package com.sjp.recruitment.service.vad;

import java.util.List;

public record VadAnalysisResult(
        Status status,
        String errorCode,
        String modelVersion,
        List<SpeechSegment> speechSegments,
        double audioDurationSeconds,
        double speakingDurationSeconds,
        Double speechOnsetSeconds,
        Double speechEndSeconds,
        int internalPauseCount,
        double longestInternalPauseSeconds,
        double totalInternalPauseDurationSeconds,
        double pauseDurationRatio,
        long decodeDurationMillis,
        long inferenceDurationMillis
) {

    public VadAnalysisResult {
        speechSegments = speechSegments == null ? List.of() : List.copyOf(speechSegments);
    }

    public boolean completed() {
        return status == Status.COMPLETED;
    }

    public enum Status {
        COMPLETED,
        DISABLED,
        DECODER_UNAVAILABLE,
        MODEL_UNAVAILABLE,
        INVALID_AUDIO,
        DECODE_FAILED,
        INFERENCE_FAILED,
        BUSY
    }

    public record SpeechSegment(long startMs, long endMs) {
        public SpeechSegment {
            if (startMs < 0 || endMs <= startMs) {
                throw new IllegalArgumentException("Speech segment time range is invalid");
            }
        }
    }
}
