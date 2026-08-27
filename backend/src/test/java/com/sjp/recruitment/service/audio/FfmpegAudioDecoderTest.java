package com.sjp.recruitment.service.audio;

import com.sjp.recruitment.config.VadProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FfmpegAudioDecoderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsUnavailableWhenAbsoluteExecutableIsNotConfigured() {
        VadProperties properties = properties("");
        FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(properties);
        decoder.initialize();
        try {
            assertFalse(decoder.health().available());
            assertEquals("path_missing", decoder.health().code());
        } finally {
            decoder.shutdown();
        }
    }

    @Test
    void validatesEmptyInputSequenceEmptySegmentAndMimeBeforeStartingNativeProcess() {
        VadProperties properties = properties("");
        FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(properties);
        decoder.initialize();
        try {
            assertCode(AudioDecodingException.Code.EMPTY_INPUT, () -> decoder.decode(List.of()));
            assertCode(AudioDecodingException.Code.INVALID_SEQUENCE, () -> decoder.decode(List.of(
                    AudioCaptureSegment.fromBytes(1, new byte[]{1}, "audio/webm"),
                    AudioCaptureSegment.fromBytes(3, new byte[]{2}, "audio/webm")
            )));
            assertCode(AudioDecodingException.Code.INVALID_SEQUENCE, () -> decoder.decode(List.of(
                    AudioCaptureSegment.fromBytes(1, new byte[]{1}, "audio/webm"),
                    AudioCaptureSegment.fromBytes(1, new byte[]{2}, "audio/webm")
            )));
            assertCode(AudioDecodingException.Code.EMPTY_SEGMENT, () -> decoder.decode(List.of(
                    AudioCaptureSegment.fromBytes(0, new byte[0], "audio/webm")
            )));
            assertCode(AudioDecodingException.Code.UNSUPPORTED_MIME_TYPE, () -> decoder.decode(List.of(
                    AudioCaptureSegment.fromBytes(0, new byte[]{1}, "application/octet-stream")
            )));
        } finally {
            decoder.shutdown();
        }
    }

    @Test
    void decodesEachCaptureSeparatelyAndConcatenatesPcmBySequence() throws Exception {
        Path ffmpeg = configuredFfmpeg();
        Path lowTone = generateWebm(ffmpeg, 440, 0.40, "low.webm");
        Path highTone = generateWebm(ffmpeg, 880, 0.30, "high.webm");
        FfmpegAudioDecoder decoder = initializedDecoder(ffmpeg);
        try {
            DecodedPcmAudio decoded = decoder.decode(List.of(
                    AudioCaptureSegment.fromPath(2, highTone, "audio/webm;codecs=opus"),
                    AudioCaptureSegment.fromPath(1, lowTone, "audio/webm;codecs=opus")
            ));

            assertEquals(VadProperties.SAMPLE_RATE, decoded.sampleRate());
            assertEquals(0.70, decoded.durationSeconds(), 0.08);
            short[] samples = decoded.samples();
            int lowCrossings = zeroCrossings(samples, 0, Math.min(samples.length, 5_600));
            int highStart = Math.min(samples.length, 7_000);
            int highCrossings = zeroCrossings(samples, highStart, Math.min(samples.length, highStart + 4_000));
            assertTrue(lowCrossings > 100, "The first decoded capture should contain the low tone");
            assertTrue(highCrossings > lowCrossings, "The second decoded capture should have a higher frequency");
        } finally {
            decoder.shutdown();
        }
    }

    @Test
    void rejectsMalformedAndTruncatedWebmWithControlledErrors() throws Exception {
        Path ffmpeg = configuredFfmpeg();
        Path valid = generateWebm(ffmpeg, 440, 0.5, "valid.webm");
        byte[] validBytes = Files.readAllBytes(valid);
        byte[] truncated = Arrays.copyOf(validBytes, Math.min(80, validBytes.length));
        FfmpegAudioDecoder decoder = initializedDecoder(ffmpeg);
        try {
            assertDecodeFailure(() -> decoder.decode(List.of(
                    AudioCaptureSegment.fromBytes(0, new byte[]{1, 2, 3, 4, 5}, "audio/webm")
            )));
            assertDecodeFailure(() -> decoder.decode(List.of(
                    AudioCaptureSegment.fromBytes(0, truncated, "audio/webm")
            )));
        } finally {
            decoder.shutdown();
        }
    }

    @Test
    void enforcesDecodedDurationAndOutputSize() throws Exception {
        Path ffmpeg = configuredFfmpeg();
        Path twoSeconds = generateWebm(ffmpeg, 440, 2.0, "long.webm");

        VadProperties durationProperties = properties(ffmpeg.toString());
        durationProperties.setMaxDurationSeconds(1);
        durationProperties.setMaxOutputBytes(128_000);
        FfmpegAudioDecoder durationDecoder = new FfmpegAudioDecoder(durationProperties);
        durationDecoder.initialize();
        try {
            assertCode(AudioDecodingException.Code.OUTPUT_TOO_LONG, () -> durationDecoder.decode(List.of(
                    AudioCaptureSegment.fromPath(0, twoSeconds, "audio/webm")
            )));
        } finally {
            durationDecoder.shutdown();
        }

        VadProperties sizeProperties = properties(ffmpeg.toString());
        sizeProperties.setMaxOutputBytes(4_000);
        FfmpegAudioDecoder sizeDecoder = new FfmpegAudioDecoder(sizeProperties);
        sizeDecoder.initialize();
        try {
            assertCode(AudioDecodingException.Code.OUTPUT_TOO_LARGE, () -> sizeDecoder.decode(List.of(
                    AudioCaptureSegment.fromPath(0, twoSeconds, "audio/webm")
            )));
        } finally {
            sizeDecoder.shutdown();
        }
    }

    private FfmpegAudioDecoder initializedDecoder(Path ffmpeg) {
        FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(properties(ffmpeg.toString()));
        decoder.initialize();
        assertTrue(decoder.health().available(), decoder.health().detail());
        return decoder;
    }

    private VadProperties properties(String ffmpegPath) {
        VadProperties properties = new VadProperties();
        properties.setFfmpegPath(ffmpegPath);
        properties.setDecodeTimeout(Duration.ofSeconds(15));
        properties.setHealthCheckTimeout(Duration.ofSeconds(3));
        return properties;
    }

    private Path configuredFfmpeg() {
        String configured = System.getProperty("vad.test.ffmpeg", "");
        assumeTrue(!configured.isBlank(), "Set -Dvad.test.ffmpeg=<absolute path> to run FFmpeg integration tests");
        Path path = Path.of(configured);
        assumeTrue(path.isAbsolute() && Files.isRegularFile(path), "Configured FFmpeg test path is invalid");
        return path;
    }

    private Path generateWebm(Path ffmpeg, int frequency, double seconds, String filename) throws Exception {
        Path output = temporaryDirectory.resolve(filename);
        Process process = new ProcessBuilder(List.of(
                ffmpeg.toString(),
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-y",
                "-f", "lavfi",
                "-i", "sine=frequency=" + frequency + ":duration=" + seconds + ":sample_rate=48000",
                "-c:a", "libopus",
                "-b:a", "64k",
                output.toString()
        )).redirectErrorStream(true).start();
        byte[] processOutput = process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS), "FFmpeg fixture generation timed out");
        assertEquals(0, process.exitValue(), new String(processOutput, java.nio.charset.StandardCharsets.UTF_8));
        return output;
    }

    private int zeroCrossings(short[] samples, int start, int end) {
        int crossings = 0;
        for (int index = Math.max(1, start + 1); index < end; index++) {
            if ((samples[index - 1] < 0 && samples[index] >= 0)
                    || (samples[index - 1] >= 0 && samples[index] < 0)) {
                crossings++;
            }
        }
        return crossings;
    }

    private void assertCode(AudioDecodingException.Code code, ThrowingRunnable action) {
        AudioDecodingException exception = assertThrows(AudioDecodingException.class, action::run);
        assertEquals(code, exception.getCode());
    }

    private void assertDecodeFailure(ThrowingRunnable action) {
        AudioDecodingException exception = assertThrows(AudioDecodingException.class, action::run);
        assertTrue(
                exception.getCode() == AudioDecodingException.Code.DECODE_FAILED
                        || exception.getCode() == AudioDecodingException.Code.INVALID_PCM,
                "Unexpected controlled error code: " + exception.getCode()
        );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws IOException;
    }
}
