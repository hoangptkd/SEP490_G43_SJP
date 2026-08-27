package com.sjp.recruitment.service.audio;

import java.util.Arrays;

public final class DecodedPcmAudio {

    private final short[] samples;
    private final int sampleRate;

    public DecodedPcmAudio(short[] samples, int sampleRate) {
        if (samples == null) {
            throw new IllegalArgumentException("PCM samples are required");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive");
        }
        this.samples = Arrays.copyOf(samples, samples.length);
        this.sampleRate = sampleRate;
    }

    public short[] samples() {
        return Arrays.copyOf(samples, samples.length);
    }

    public int sampleRate() {
        return sampleRate;
    }

    public int sampleCount() {
        return samples.length;
    }

    public double durationSeconds() {
        return samples.length / (double) sampleRate;
    }
}
