package com.sjp.recruitment.service.vad;

record VadInferenceOutput(float speechProbability, float[] recurrentState) {
}
