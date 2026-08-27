package com.sjp.recruitment.service.vad;

import com.sjp.recruitment.config.VadProperties;

public final class SileroInferenceState {

    private float[] recurrentState = new float[2 * 1 * 128];
    private final float[] context = new float[VadProperties.CONTEXT_SAMPLES];
    private long sampleCursor;

    float[] recurrentStateView() {
        return recurrentState;
    }

    void copyContextInto(float[] destination) {
        if (destination == null || destination.length < context.length) {
            throw new IllegalArgumentException("Model input cannot hold Silero context");
        }
        System.arraycopy(context, 0, destination, 0, context.length);
    }

    long sampleCursor() {
        return sampleCursor;
    }

    void advance(float[] nextState, float[] frame, int realSamples) {
        if (nextState == null || nextState.length != recurrentState.length) {
            throw new VadAnalysisException("invalid_state", "Silero returned an invalid recurrent state");
        }
        recurrentState = nextState;
        System.arraycopy(
                frame,
                VadProperties.FRAME_SAMPLES - VadProperties.CONTEXT_SAMPLES,
                context,
                0,
                VadProperties.CONTEXT_SAMPLES
        );
        sampleCursor += realSamples;
    }
}
