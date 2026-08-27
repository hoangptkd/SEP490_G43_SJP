package com.sjp.recruitment.service.vad;

import com.sjp.recruitment.config.VadProperties;
import com.sjp.recruitment.service.audio.AudioCaptureSegment;
import com.sjp.recruitment.service.audio.FfmpegAudioDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledOnOs(OS.LINUX)
@EnabledIfSystemProperty(named = "vad.performance.enabled", matches = "true")
class SileroVadPerformanceLinuxTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void benchmarksThirtyAndOneHundredEightySecondAudio() throws Exception {
        Path ffmpeg = configuredFfmpeg();
        VadProperties properties = new VadProperties();
        properties.setFfmpegPath(ffmpeg.toString());
        properties.setDecodeTimeout(Duration.ofSeconds(60));
        properties.setHealthCheckTimeout(Duration.ofSeconds(5));
        properties.setMaxDurationSeconds(180);
        properties.setMaxOutputBytes(5_760_000L);
        properties.setMaxConcurrentAnalyses(1);

        FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(properties);
        SileroModelRuntime model = new SileroModelRuntime(properties, new DefaultResourceLoader());
        decoder.initialize();
        model.initialize();
        try {
            assertTrue(decoder.health().available(), decoder.health().detail());
            assertTrue(model.health().available(), model.health().detail());
            SileroVadAnalyzer analyzer = new SileroVadAnalyzer(decoder, model, properties);
            benchmark(ffmpeg, analyzer, 30);
            benchmark(ffmpeg, analyzer, 180);
        } finally {
            decoder.shutdown();
            model.close();
        }
    }

    private void benchmark(Path ffmpeg, SileroVadAnalyzer analyzer, int seconds) throws Exception {
        Path fixture = generateWebm(ffmpeg, seconds);
        long heapBefore = usedHeap();
        VadAnalysisResult result = analyzer.analyze(List.of(
                AudioCaptureSegment.fromPath(0, fixture, "audio/webm;codecs=opus")
        ));
        long heapAfter = usedHeap();

        assertEquals(VadAnalysisResult.Status.COMPLETED, result.status(), result.errorCode());
        assertEquals(seconds, result.audioDurationSeconds(), 0.08);
        long approximateHeapDelta = Math.max(0, heapAfter - heapBefore);
        System.out.printf(
                "VAD_PERF duration=%ds decodeMs=%d inferenceMs=%d approxHeapDeltaBytes=%d inputBytes=%d%n",
                seconds,
                result.decodeDurationMillis(),
                result.inferenceDurationMillis(),
                approximateHeapDelta,
                Files.size(fixture)
        );
    }

    private Path generateWebm(Path ffmpeg, int seconds) throws Exception {
        Path output = temporaryDirectory.resolve("benchmark-" + seconds + ".webm");
        Process process = new ProcessBuilder(List.of(
                ffmpeg.toString(),
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-y",
                "-f", "lavfi",
                "-i", "sine=frequency=440:duration=" + seconds + ":sample_rate=48000",
                "-c:a", "libopus",
                "-b:a", "48k",
                output.toString()
        )).redirectErrorStream(true).start();
        byte[] outputText = process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "FFmpeg benchmark fixture generation timed out");
        assertEquals(0, process.exitValue(), new String(outputText, java.nio.charset.StandardCharsets.UTF_8));
        return output;
    }

    private Path configuredFfmpeg() {
        String configured = System.getProperty("vad.test.ffmpeg", "");
        assumeTrue(!configured.isBlank(), "Set -Dvad.test.ffmpeg=<absolute path>");
        Path path = Path.of(configured);
        assumeTrue(path.isAbsolute() && Files.isRegularFile(path));
        return path;
    }

    private long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
