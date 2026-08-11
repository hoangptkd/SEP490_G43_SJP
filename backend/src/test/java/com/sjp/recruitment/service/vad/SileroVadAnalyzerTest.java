package com.sjp.recruitment.service.vad;

import com.sjp.recruitment.config.VadProperties;
import com.sjp.recruitment.service.audio.AudioCaptureSegment;
import com.sjp.recruitment.service.audio.AudioDecoder;
import com.sjp.recruitment.service.audio.AudioDecoderHealth;
import com.sjp.recruitment.service.audio.DecodedPcmAudio;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SileroVadAnalyzerTest {

    @Test
    void silenceOnlyProducesNoSpeechAndNoOnset() {
        SileroVadAnalyzer analyzer = analyzer(new AmplitudeVadModel());

        VadAnalysisResult result = analyzer.analyze(pcmFrames(10, false));

        assertEquals(VadAnalysisResult.Status.COMPLETED, result.status());
        assertTrue(result.speechSegments().isEmpty());
        assertEquals(0.0, result.speakingDurationSeconds());
        assertNull(result.speechOnsetSeconds());
        assertNull(result.speechEndSeconds());
        assertEquals(0, result.internalPauseCount());
        assertEquals(0.0, result.pauseDurationRatio());
    }

    @Test
    void continuousSpeechProducesSpeechSegmentAndPositiveDuration() {
        SileroVadAnalyzer analyzer = analyzer(new AmplitudeVadModel());

        VadAnalysisResult result = analyzer.analyze(pcmFrames(10, true));

        assertEquals(VadAnalysisResult.Status.COMPLETED, result.status());
        assertTrue(result.speechSegments().size() >= 1);
        assertTrue(result.speakingDurationSeconds() > 0.0);
        assertNotNull(result.speechOnsetSeconds());
        assertNotNull(result.speechEndSeconds());
        assertEquals(0, result.internalPauseCount());
    }

    @Test
    void speechSilenceSpeechProducesOnlyInternalPauseMetrics() {
        SileroVadAnalyzer analyzer = analyzer(new AmplitudeVadModel());
        short[] samples = concatenate(
                samplesForFrames(2, false),
                samplesForFrames(4, true),
                samplesForFrames(4, false),
                samplesForFrames(4, true),
                samplesForFrames(3, false)
        );

        VadAnalysisResult result = analyzer.analyze(new DecodedPcmAudio(samples, VadProperties.SAMPLE_RATE));

        assertEquals(VadAnalysisResult.Status.COMPLETED, result.status());
        assertEquals(2, result.speechSegments().size());
        assertEquals(1, result.internalPauseCount());
        assertTrue(result.totalInternalPauseDurationSeconds() > 0.0);
        assertTrue(result.longestInternalPauseSeconds() > 0.0);
        assertTrue(result.pauseDurationRatio() > 0.0);
        assertTrue(result.speechOnsetSeconds() > 0.0, "Leading silence is retained as onset, not counted as an internal pause");
        assertTrue(result.speechEndSeconds() < result.audioDurationSeconds(), "Trailing silence is not counted as an internal pause");
    }

    @Test
    void finalPartialFrameUsesRealAudioDurationInsteadOfPadding() {
        CountingVadModel model = new CountingVadModel(0.9f);
        SileroVadAnalyzer analyzer = analyzer(model);
        short[] samples = new short[700];
        Arrays.fill(samples, (short) 12_000);

        VadAnalysisResult result = analyzer.analyze(new DecodedPcmAudio(samples, VadProperties.SAMPLE_RATE));

        assertEquals(2, model.calls.get());
        assertEquals(700 / 16_000.0, result.audioDurationSeconds(), 1.0e-12);
        assertEquals(result.audioDurationSeconds(), result.speechEndSeconds(), 1.0e-12);
    }

    @Test
    void repeatedAnalysisIsDeterministic() {
        SileroVadAnalyzer analyzer = analyzer(new AmplitudeVadModel());
        DecodedPcmAudio pcm = pcmFrames(12, true);

        VadAnalysisResult first = analyzer.analyze(pcm);
        VadAnalysisResult second = analyzer.analyze(pcm);

        assertEquivalentMetrics(first, second);
    }

    @Test
    void concurrentAnswersStartWithIndependentZeroState() throws Exception {
        FirstFrameStateCheckingModel model = new FirstFrameStateCheckingModel();
        VadProperties properties = properties();
        properties.setMaxConcurrentAnalyses(4);
        SileroVadAnalyzer analyzer = new SileroVadAnalyzer(
                new StubAudioDecoder(),
                model,
                properties,
                new Semaphore(4, true)
        );
        DecodedPcmAudio oneFrame = pcmFrames(1, true);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<VadAnalysisResult>> tasks = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> (Callable<VadAnalysisResult>) () -> analyzer.analyze(oneFrame))
                    .toList();
            List<Future<VadAnalysisResult>> futures = executor.invokeAll(tasks);
            for (Future<VadAnalysisResult> future : futures) {
                assertEquals(VadAnalysisResult.Status.COMPLETED, future.get(5, TimeUnit.SECONDS).status());
            }
            assertEquals(20, model.zeroStateCalls.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void decoderFailuresDegradeToControlledStatusWhenVadIsOptional() {
        VadProperties properties = properties();
        AudioDecoder unavailable = new AudioDecoder() {
            @Override
            public DecodedPcmAudio decode(List<AudioCaptureSegment> segments) {
                throw new AssertionError("Unavailable decoder must not be invoked");
            }

            @Override
            public AudioDecoderHealth health() {
                return AudioDecoderHealth.unavailable("path_missing", "not configured");
            }
        };
        SileroVadAnalyzer analyzer = new SileroVadAnalyzer(
                unavailable,
                new AmplitudeVadModel(),
                properties,
                new Semaphore(1)
        );

        VadAnalysisResult result = analyzer.analyze(List.of(
                AudioCaptureSegment.fromBytes(0, new byte[]{1}, "audio/webm")
        ));

        assertEquals(VadAnalysisResult.Status.DECODER_UNAVAILABLE, result.status());
        assertEquals("path_missing", result.errorCode());
    }

    @Test
    void inferenceFailureDegradesWithoutReturningPartialMetrics() {
        VadModel failing = new AmplitudeVadModel() {
            @Override
            public VadInferenceOutput infer(float[] inputWithContext, float[] recurrentState) {
                throw new VadAnalysisException("synthetic_inference_failure", "test failure");
            }
        };

        VadAnalysisResult result = analyzer(failing).analyze(pcmFrames(2, true));

        assertEquals(VadAnalysisResult.Status.INFERENCE_FAILED, result.status());
        assertEquals("synthetic_inference_failure", result.errorCode());
        assertTrue(result.speechSegments().isEmpty());
        assertEquals(0.0, result.speakingDurationSeconds());
    }

    @Test
    void exhaustedConcurrencyReturnsBusyStatus() {
        VadProperties properties = properties();
        properties.setConcurrencyAcquireTimeout(Duration.ofMillis(1));
        SileroVadAnalyzer analyzer = new SileroVadAnalyzer(
                new StubAudioDecoder(),
                new AmplitudeVadModel(),
                properties,
                new Semaphore(0)
        );

        VadAnalysisResult result = analyzer.analyze(pcmFrames(1, true));

        assertEquals(VadAnalysisResult.Status.BUSY, result.status());
        assertEquals("vad_concurrency_limit", result.errorCode());
    }

    private SileroVadAnalyzer analyzer(VadModel model) {
        VadProperties properties = properties();
        return new SileroVadAnalyzer(new StubAudioDecoder(), model, properties, new Semaphore(2, true));
    }

    private VadProperties properties() {
        VadProperties properties = new VadProperties();
        properties.setConcurrencyAcquireTimeout(Duration.ofSeconds(1));
        properties.getSegmentation().setMinSpeechDurationMs(32);
        properties.getSegmentation().setMinSilenceDurationMs(64);
        properties.getSegmentation().setSpeechPadMs(0);
        return properties;
    }

    private DecodedPcmAudio pcmFrames(int frames, boolean speech) {
        return new DecodedPcmAudio(samplesForFrames(frames, speech), VadProperties.SAMPLE_RATE);
    }

    private short[] samplesForFrames(int frames, boolean speech) {
        short[] samples = new short[frames * VadProperties.FRAME_SAMPLES];
        if (speech) {
            Arrays.fill(samples, (short) 12_000);
        }
        return samples;
    }

    private short[] concatenate(short[]... parts) {
        int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        short[] combined = new short[length];
        int offset = 0;
        for (short[] part : parts) {
            System.arraycopy(part, 0, combined, offset, part.length);
            offset += part.length;
        }
        return combined;
    }

    private void assertEquivalentMetrics(VadAnalysisResult first, VadAnalysisResult second) {
        assertEquals(first.status(), second.status());
        assertEquals(first.speechSegments(), second.speechSegments());
        assertEquals(first.audioDurationSeconds(), second.audioDurationSeconds());
        assertEquals(first.speakingDurationSeconds(), second.speakingDurationSeconds());
        assertEquals(first.speechOnsetSeconds(), second.speechOnsetSeconds());
        assertEquals(first.speechEndSeconds(), second.speechEndSeconds());
        assertEquals(first.internalPauseCount(), second.internalPauseCount());
        assertEquals(first.longestInternalPauseSeconds(), second.longestInternalPauseSeconds());
        assertEquals(first.totalInternalPauseDurationSeconds(), second.totalInternalPauseDurationSeconds());
        assertEquals(first.pauseDurationRatio(), second.pauseDurationRatio());
    }

    private static final class StubAudioDecoder implements AudioDecoder {
        @Override
        public DecodedPcmAudio decode(List<AudioCaptureSegment> segments) {
            throw new UnsupportedOperationException("PCM tests bypass container decoding");
        }

        @Override
        public AudioDecoderHealth health() {
            return AudioDecoderHealth.available("test decoder");
        }
    }

    private static class AmplitudeVadModel implements VadModel {
        @Override
        public VadInferenceOutput infer(float[] inputWithContext, float[] recurrentState) {
            boolean speech = false;
            for (int index = VadProperties.CONTEXT_SAMPLES; index < inputWithContext.length; index++) {
                if (Math.abs(inputWithContext[index]) > 0.1f) {
                    speech = true;
                    break;
                }
            }
            float[] nextState = Arrays.copyOf(recurrentState, recurrentState.length);
            nextState[0] += 1.0f;
            return new VadInferenceOutput(speech ? 0.9f : 0.1f, nextState);
        }

        @Override
        public SileroModelHealth health() {
            return SileroModelHealth.available(null);
        }

        @Override
        public String modelVersion() {
            return "test-model";
        }
    }

    private static final class CountingVadModel extends AmplitudeVadModel {
        private final AtomicInteger calls = new AtomicInteger();
        private final float probability;

        private CountingVadModel(float probability) {
            this.probability = probability;
        }

        @Override
        public VadInferenceOutput infer(float[] inputWithContext, float[] recurrentState) {
            calls.incrementAndGet();
            float[] nextState = Arrays.copyOf(recurrentState, recurrentState.length);
            nextState[0] += 1.0f;
            return new VadInferenceOutput(probability, nextState);
        }
    }

    private static final class FirstFrameStateCheckingModel extends AmplitudeVadModel {
        private final AtomicInteger zeroStateCalls = new AtomicInteger();

        @Override
        public VadInferenceOutput infer(float[] inputWithContext, float[] recurrentState) {
            boolean allZero = true;
            for (float value : recurrentState) {
                if (value != 0.0f) {
                    allZero = false;
                    break;
                }
            }
            if (!allZero) {
                throw new AssertionError("A one-frame answer received recurrent state from another answer");
            }
            zeroStateCalls.incrementAndGet();
            return super.infer(inputWithContext, recurrentState);
        }
    }
}
