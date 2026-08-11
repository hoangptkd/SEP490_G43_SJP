package com.sjp.recruitment.service.vad;

interface VadModel {

    VadInferenceOutput infer(float[] inputWithContext, float[] recurrentState);

    SileroModelHealth health();

    String modelVersion();
}
