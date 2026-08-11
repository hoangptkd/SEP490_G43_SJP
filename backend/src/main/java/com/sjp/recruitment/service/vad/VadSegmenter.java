package com.sjp.recruitment.service.vad;

import com.sjp.recruitment.config.VadProperties;

import java.util.ArrayList;
import java.util.List;

public final class VadSegmenter {

    private final int sampleRate;
    private final float speechThreshold;
    private final float negativeThreshold;
    private final long minSpeechSamples;
    private final long minSilenceSamples;
    private final long speechPadSamples;
    private final List<SampleSegment> rawSegments = new ArrayList<>();

    private boolean speechActive;
    private long speechStart = -1;
    private long candidateEnd = -1;

    public VadSegmenter(VadProperties.Segmentation config, int sampleRate) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive");
        }
        if (!(config.getSpeechThreshold() > 0.0f && config.getSpeechThreshold() < 1.0f)) {
            throw new IllegalArgumentException("Speech threshold must be between zero and one");
        }
        if (!(config.getNegativeThreshold() >= 0.0f && config.getNegativeThreshold() < config.getSpeechThreshold())) {
            throw new IllegalArgumentException("Negative threshold must be non-negative and lower than speech threshold");
        }
        if (config.getMinSpeechDurationMs() < 0 || config.getMinSilenceDurationMs() < 0 || config.getSpeechPadMs() < 0) {
            throw new IllegalArgumentException("Segmentation durations must not be negative");
        }
        this.sampleRate = sampleRate;
        this.speechThreshold = config.getSpeechThreshold();
        this.negativeThreshold = config.getNegativeThreshold();
        this.minSpeechSamples = millisecondsToSamples(config.getMinSpeechDurationMs());
        this.minSilenceSamples = millisecondsToSamples(config.getMinSilenceDurationMs());
        this.speechPadSamples = millisecondsToSamples(config.getSpeechPadMs());
    }

    public void accept(float speechProbability, long frameStartSample, int realFrameSamples) {
        if (!Float.isFinite(speechProbability) || frameStartSample < 0 || realFrameSamples <= 0) {
            throw new VadAnalysisException("invalid_segmentation_input", "Invalid VAD frame metadata or probability");
        }
        long frameEnd = frameStartSample + realFrameSamples;

        if (speechProbability >= speechThreshold) {
            if (!speechActive) {
                speechActive = true;
                speechStart = frameStartSample;
            }
            candidateEnd = -1;
            return;
        }

        if (speechActive && speechProbability < negativeThreshold) {
            if (candidateEnd < 0) {
                candidateEnd = frameStartSample;
            }
            if (frameEnd - candidateEnd >= minSilenceSamples) {
                closeRawSegment(candidateEnd);
            }
        }
    }

    public List<SampleSegment> finish(long totalSamples) {
        if (totalSamples < 0) {
            throw new IllegalArgumentException("Total sample count must not be negative");
        }
        if (speechActive) {
            closeRawSegment(totalSamples);
        }

        List<SampleSegment> padded = new ArrayList<>();
        for (SampleSegment segment : rawSegments) {
            long start = Math.max(0, segment.startSample() - speechPadSamples);
            long end = Math.min(totalSamples, segment.endSample() + speechPadSamples);
            if (end <= start) {
                continue;
            }
            if (!padded.isEmpty() && start <= padded.get(padded.size() - 1).endSample()) {
                SampleSegment previous = padded.remove(padded.size() - 1);
                padded.add(new SampleSegment(previous.startSample(), Math.max(previous.endSample(), end)));
            } else {
                padded.add(new SampleSegment(start, end));
            }
        }
        return List.copyOf(padded);
    }

    private void closeRawSegment(long endSample) {
        if (speechStart >= 0 && endSample > speechStart && endSample - speechStart >= minSpeechSamples) {
            rawSegments.add(new SampleSegment(speechStart, endSample));
        }
        speechActive = false;
        speechStart = -1;
        candidateEnd = -1;
    }

    private long millisecondsToSamples(int milliseconds) {
        return Math.round(sampleRate * (milliseconds / 1000.0));
    }

    public record SampleSegment(long startSample, long endSample) {
        public SampleSegment {
            if (startSample < 0 || endSample <= startSample) {
                throw new IllegalArgumentException("Speech segment sample range is invalid");
            }
        }
    }
}
