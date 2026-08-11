package com.sjp.recruitment.service.vad;

import com.sjp.recruitment.config.VadProperties;
import com.sjp.recruitment.service.audio.AudioCaptureSegment;
import com.sjp.recruitment.service.audio.AudioDecoder;
import com.sjp.recruitment.service.audio.AudioDecodingException;
import com.sjp.recruitment.service.audio.DecodedPcmAudio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class SileroVadAnalyzer {

    private final AudioDecoder decoder;
    private final VadModel model;
    private final VadProperties properties;
    private final Semaphore concurrency;

    @Autowired
    public SileroVadAnalyzer(AudioDecoder decoder, SileroModelRuntime model, VadProperties properties) {
        this(decoder, model, properties, new Semaphore(Math.max(1, properties.getMaxConcurrentAnalyses()), true));
    }

    SileroVadAnalyzer(AudioDecoder decoder, VadModel model, VadProperties properties, Semaphore concurrency) {
        this.decoder = decoder;
        this.model = model;
        this.properties = properties;
        this.concurrency = concurrency;
    }

    public VadAnalysisResult analyze(List<AudioCaptureSegment> segments) {
        if (!properties.isEnabled()) {
            return unavailable(VadAnalysisResult.Status.DISABLED, "vad_disabled", 0, 0);
        }
        if (!decoder.health().available()) {
            return requiredOrUnavailable(VadAnalysisResult.Status.DECODER_UNAVAILABLE, decoder.health().code(), 0, 0);
        }
        if (!model.health().available()) {
            return requiredOrUnavailable(VadAnalysisResult.Status.MODEL_UNAVAILABLE, model.health().code(), 0, 0);
        }

        boolean acquired = false;
        try {
            acquired = concurrency.tryAcquire(
                    properties.getConcurrencyAcquireTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
            if (!acquired) {
                return requiredOrUnavailable(VadAnalysisResult.Status.BUSY, "vad_concurrency_limit", 0, 0);
            }

            long decodeStarted = System.nanoTime();
            DecodedPcmAudio pcm = decoder.decode(segments);
            long decodeMillis = elapsedMillis(decodeStarted);
            return analyzePcmInternal(pcm, decodeMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return requiredOrUnavailable(VadAnalysisResult.Status.BUSY, "vad_interrupted", 0, 0);
        } catch (AudioDecodingException exception) {
            VadAnalysisResult.Status status = switch (exception.getCode()) {
                case DECODER_UNAVAILABLE -> VadAnalysisResult.Status.DECODER_UNAVAILABLE;
                case EMPTY_INPUT, INVALID_SEQUENCE, EMPTY_SEGMENT, UNSUPPORTED_MIME_TYPE, INPUT_TOO_LARGE,
                     INVALID_SOURCE, OUTPUT_TOO_LARGE, OUTPUT_TOO_LONG, INVALID_PCM -> VadAnalysisResult.Status.INVALID_AUDIO;
                case DECODE_TIMEOUT, DECODE_FAILED -> VadAnalysisResult.Status.DECODE_FAILED;
            };
            return requiredOrUnavailable(status, exception.getCode().name().toLowerCase(), 0, 0);
        } finally {
            if (acquired) {
                concurrency.release();
            }
        }
    }

    public VadAnalysisResult analyze(DecodedPcmAudio pcm) {
        if (!properties.isEnabled()) {
            return unavailable(VadAnalysisResult.Status.DISABLED, "vad_disabled", 0, 0);
        }
        if (!model.health().available()) {
            return requiredOrUnavailable(VadAnalysisResult.Status.MODEL_UNAVAILABLE, model.health().code(), 0, 0);
        }
        boolean acquired = false;
        try {
            acquired = concurrency.tryAcquire(
                    properties.getConcurrencyAcquireTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
            if (!acquired) {
                return requiredOrUnavailable(VadAnalysisResult.Status.BUSY, "vad_concurrency_limit", 0, 0);
            }
            return analyzePcmInternal(pcm, 0);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return requiredOrUnavailable(VadAnalysisResult.Status.BUSY, "vad_interrupted", 0, 0);
        } finally {
            if (acquired) {
                concurrency.release();
            }
        }
    }

    private VadAnalysisResult analyzePcmInternal(DecodedPcmAudio pcm, long decodeMillis) {
        if (pcm == null || pcm.sampleRate() != VadProperties.SAMPLE_RATE || pcm.sampleCount() == 0) {
            return requiredOrUnavailable(VadAnalysisResult.Status.INVALID_AUDIO, "invalid_pcm", decodeMillis, 0);
        }
        if (pcm.durationSeconds() > properties.getMaxDurationSeconds()
                || (long) pcm.sampleCount() * Short.BYTES > properties.getMaxOutputBytes()) {
            return requiredOrUnavailable(VadAnalysisResult.Status.INVALID_AUDIO, "pcm_limit_exceeded", decodeMillis, 0);
        }

        long inferenceStarted = System.nanoTime();
        try {
            short[] samples = pcm.samples();
            SileroInferenceState state = new SileroInferenceState();
            VadSegmenter segmenter = new VadSegmenter(properties.getSegmentation(), VadProperties.SAMPLE_RATE);
            float[] frame = new float[VadProperties.FRAME_SAMPLES];
            float[] modelInput = new float[VadProperties.CONTEXT_SAMPLES + VadProperties.FRAME_SAMPLES];

            for (int offset = 0; offset < samples.length; offset += VadProperties.FRAME_SAMPLES) {
                int realSamples = Math.min(VadProperties.FRAME_SAMPLES, samples.length - offset);
                for (int index = 0; index < realSamples; index++) {
                    frame[index] = samples[offset + index] / 32768.0f;
                }
                if (realSamples < VadProperties.FRAME_SAMPLES) {
                    java.util.Arrays.fill(frame, realSamples, VadProperties.FRAME_SAMPLES, 0.0f);
                }
                state.copyContextInto(modelInput);
                System.arraycopy(frame, 0, modelInput, VadProperties.CONTEXT_SAMPLES, VadProperties.FRAME_SAMPLES);

                VadInferenceOutput output = model.infer(modelInput, state.recurrentStateView());
                segmenter.accept(output.speechProbability(), state.sampleCursor(), realSamples);
                state.advance(output.recurrentState(), frame, realSamples);
            }

            List<VadSegmenter.SampleSegment> sampleSegments = segmenter.finish(samples.length);
            return completedResult(sampleSegments, samples.length, decodeMillis, elapsedMillis(inferenceStarted));
        } catch (RuntimeException exception) {
            if (properties.isRequired()) {
                throw exception instanceof VadAnalysisException vadException
                        ? vadException
                        : new VadAnalysisException("inference_failed", "Silero VAD analysis failed", exception);
            }
            return unavailable(
                    VadAnalysisResult.Status.INFERENCE_FAILED,
                    exception instanceof VadAnalysisException vadException ? vadException.getCode() : "inference_failed",
                    decodeMillis,
                    elapsedMillis(inferenceStarted)
            );
        }
    }

    private VadAnalysisResult completedResult(
            List<VadSegmenter.SampleSegment> segments,
            long totalSamples,
            long decodeMillis,
            long inferenceMillis
    ) {
        List<VadAnalysisResult.SpeechSegment> outputSegments = new ArrayList<>(segments.size());
        long speakingSamples = 0;
        long totalPauseSamples = 0;
        long longestPauseSamples = 0;
        int pauseCount = 0;

        for (int index = 0; index < segments.size(); index++) {
            VadSegmenter.SampleSegment segment = segments.get(index);
            speakingSamples += segment.endSample() - segment.startSample();
            long startMs = sampleToStartMilliseconds(segment.startSample());
            long endMs = sampleToEndMilliseconds(segment.endSample());
            outputSegments.add(new VadAnalysisResult.SpeechSegment(startMs, Math.max(startMs + 1, endMs)));
            if (index > 0) {
                long pauseSamples = Math.max(0, segment.startSample() - segments.get(index - 1).endSample());
                if (pauseSamples > 0) {
                    pauseCount++;
                    totalPauseSamples += pauseSamples;
                    longestPauseSamples = Math.max(longestPauseSamples, pauseSamples);
                }
            }
        }

        double denominator = speakingSamples + (double) totalPauseSamples;
        Double onset = segments.isEmpty() ? null : samplesToSeconds(segments.get(0).startSample());
        Double end = segments.isEmpty() ? null : samplesToSeconds(segments.get(segments.size() - 1).endSample());
        return new VadAnalysisResult(
                VadAnalysisResult.Status.COMPLETED,
                null,
                model.modelVersion(),
                outputSegments,
                samplesToSeconds(totalSamples),
                samplesToSeconds(speakingSamples),
                onset,
                end,
                pauseCount,
                samplesToSeconds(longestPauseSamples),
                samplesToSeconds(totalPauseSamples),
                denominator == 0.0 ? 0.0 : totalPauseSamples / denominator,
                decodeMillis,
                inferenceMillis
        );
    }

    private VadAnalysisResult requiredOrUnavailable(
            VadAnalysisResult.Status status,
            String errorCode,
            long decodeMillis,
            long inferenceMillis
    ) {
        if (properties.isRequired()) {
            throw new VadAnalysisException(errorCode, "Required VAD analysis could not complete: " + status);
        }
        return unavailable(status, errorCode, decodeMillis, inferenceMillis);
    }

    private VadAnalysisResult unavailable(
            VadAnalysisResult.Status status,
            String errorCode,
            long decodeMillis,
            long inferenceMillis
    ) {
        return new VadAnalysisResult(
                status,
                errorCode,
                model.modelVersion(),
                List.of(),
                0.0,
                0.0,
                null,
                null,
                0,
                0.0,
                0.0,
                0.0,
                decodeMillis,
                inferenceMillis
        );
    }

    private long sampleToStartMilliseconds(long sample) {
        return (sample * 1000L) / VadProperties.SAMPLE_RATE;
    }

    private long sampleToEndMilliseconds(long sample) {
        return (long) Math.ceil(sample * 1000.0 / VadProperties.SAMPLE_RATE);
    }

    private double samplesToSeconds(long samples) {
        return samples / (double) VadProperties.SAMPLE_RATE;
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}
