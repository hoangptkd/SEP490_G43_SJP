package com.sjp.recruitment.service.audio;

import com.sjp.recruitment.config.VadProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class FfmpegAudioDecoder implements AudioDecoder {

    private static final int STDERR_CAPTURE_BYTES = 64 * 1024;
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "audio/webm",
            "video/webm",
            "audio/ogg",
            "application/ogg",
            "audio/wav",
            "audio/x-wav",
            "audio/mpeg",
            "audio/mp4",
            "audio/x-m4a"
    );

    private final VadProperties properties;
    private final ExecutorService stderrExecutor;
    private volatile AudioDecoderHealth health = AudioDecoderHealth.unavailable("not_checked", "FFmpeg has not been checked");
    private volatile Path executable;

    public FfmpegAudioDecoder(VadProperties properties) {
        this.properties = properties;
        this.stderrExecutor = Executors.newFixedThreadPool(
                Math.max(1, properties.getMaxConcurrentAnalyses()),
                runnable -> {
                    Thread thread = new Thread(runnable, "vad-ffmpeg-stderr");
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    @PostConstruct
    public void initialize() {
        health = inspectExecutable();
        if (properties.isEnabled() && properties.isRequired() && !health.available()) {
            throw new IllegalStateException("Required FFmpeg decoder is unavailable: " + health.code());
        }
    }

    @Override
    public AudioDecoderHealth health() {
        return health;
    }

    @Override
    public DecodedPcmAudio decode(List<AudioCaptureSegment> segments) {
        List<AudioCaptureSegment> ordered = validateAndOrder(segments);
        if (!properties.isEnabled()) {
            throw new AudioDecodingException(AudioDecodingException.Code.DECODER_UNAVAILABLE, "VAD audio decoding is disabled");
        }
        if (!health.available() || executable == null) {
            throw new AudioDecodingException(AudioDecodingException.Code.DECODER_UNAVAILABLE, "FFmpeg decoder is unavailable");
        }

        List<short[]> decodedSegments = new ArrayList<>(ordered.size());
        long totalSamples = 0;
        long maxSamplesByDuration = (long) properties.getMaxDurationSeconds() * VadProperties.SAMPLE_RATE;
        long maxSamplesByBytes = properties.getMaxOutputBytes() / Short.BYTES;

        for (AudioCaptureSegment segment : ordered) {
            long remainingSamples = maxSamplesByBytes - totalSamples;
            if (remainingSamples <= 0) {
                throw new AudioDecodingException(AudioDecodingException.Code.OUTPUT_TOO_LARGE, "Decoded PCM exceeds the configured answer limit");
            }
            short[] decoded = decodeSegment(segment, remainingSamples * Short.BYTES);
            totalSamples += decoded.length;
            if (totalSamples > maxSamplesByBytes) {
                throw new AudioDecodingException(AudioDecodingException.Code.OUTPUT_TOO_LARGE, "Decoded PCM exceeds the configured byte limit");
            }
            if (totalSamples > maxSamplesByDuration) {
                throw new AudioDecodingException(AudioDecodingException.Code.OUTPUT_TOO_LONG, "Decoded PCM exceeds the configured duration limit");
            }
            decodedSegments.add(decoded);
        }

        short[] combined = new short[Math.toIntExact(totalSamples)];
        int offset = 0;
        for (short[] decoded : decodedSegments) {
            System.arraycopy(decoded, 0, combined, offset, decoded.length);
            offset += decoded.length;
        }
        return new DecodedPcmAudio(combined, VadProperties.SAMPLE_RATE);
    }

    private List<AudioCaptureSegment> validateAndOrder(List<AudioCaptureSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            throw new AudioDecodingException(AudioDecodingException.Code.EMPTY_INPUT, "At least one audio capture segment is required");
        }
        if (segments.stream().anyMatch(segment -> segment == null)) {
            throw new AudioDecodingException(AudioDecodingException.Code.EMPTY_SEGMENT, "Audio capture segment must not be null");
        }

        List<AudioCaptureSegment> ordered = segments.stream()
                .sorted(Comparator.comparingInt(AudioCaptureSegment::sequence))
                .toList();
        Set<Integer> sequences = new HashSet<>();
        int previous = ordered.get(0).sequence() - 1;
        for (AudioCaptureSegment segment : ordered) {
            if (segment.sequence() < 0 || !sequences.add(segment.sequence()) || segment.sequence() != previous + 1) {
                throw new AudioDecodingException(AudioDecodingException.Code.INVALID_SEQUENCE, "Audio segment sequence must be unique, non-negative and contiguous");
            }
            previous = segment.sequence();
            validateSegment(segment);
        }
        return ordered;
    }

    private void validateSegment(AudioCaptureSegment segment) {
        if (segment.hasBytes() == segment.hasPath()) {
            throw new AudioDecodingException(AudioDecodingException.Code.INVALID_SOURCE, "Each audio segment must have exactly one byte or path source");
        }
        String mimeType = normalizeMimeType(segment.mimeType());
        if (!SUPPORTED_MIME_TYPES.contains(mimeType)) {
            throw new AudioDecodingException(AudioDecodingException.Code.UNSUPPORTED_MIME_TYPE, "Unsupported audio MIME type");
        }
        if (segment.hasBytes()) {
            byte[] bytes = segment.bytes();
            if (bytes == null || bytes.length == 0) {
                throw new AudioDecodingException(AudioDecodingException.Code.EMPTY_SEGMENT, "Audio segment is empty");
            }
            if (bytes.length > properties.getMaxInputBytesPerSegment()) {
                throw new AudioDecodingException(AudioDecodingException.Code.INPUT_TOO_LARGE, "Audio segment exceeds the configured input limit");
            }
            return;
        }

        Path path = segment.path();
        try {
            if (path == null || !Files.isRegularFile(path) || Files.size(path) == 0) {
                throw new AudioDecodingException(AudioDecodingException.Code.EMPTY_SEGMENT, "Audio segment path is empty or not a regular file");
            }
            if (Files.size(path) > properties.getMaxInputBytesPerSegment()) {
                throw new AudioDecodingException(AudioDecodingException.Code.INPUT_TOO_LARGE, "Audio segment exceeds the configured input limit");
            }
        } catch (IOException exception) {
            throw new AudioDecodingException(AudioDecodingException.Code.INVALID_SOURCE, "Audio segment path cannot be inspected", exception);
        }
    }

    private short[] decodeSegment(AudioCaptureSegment segment, long remainingOutputBytes) {
        Path workDirectory = null;
        Process process = null;
        Future<String> stderrFuture = null;
        try {
            workDirectory = Files.createTempDirectory("sjp-vad-");
            Path input = workDirectory.resolve("capture-input.bin");
            Path output = workDirectory.resolve("decoded-output.pcm");
            writeTemporaryInput(segment, input);

            List<String> command = List.of(
                    executable.toString(),
                    "-nostdin",
                    "-hide_banner",
                    "-loglevel", "error",
                    "-y",
                    "-i", input.toString(),
                    "-map", "0:a:0",
                    "-vn",
                    "-ac", "1",
                    "-ar", Integer.toString(VadProperties.SAMPLE_RATE),
                    "-f", "s16le",
                    output.toString()
            );
            process = new ProcessBuilder(command)
                    .directory(workDirectory.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            Process runningProcess = process;
            stderrFuture = stderrExecutor.submit(() -> drain(runningProcess.getErrorStream()));

            waitForDecode(process, output, remainingOutputBytes);
            String stderr = getStderr(stderrFuture);
            if (process.exitValue() != 0) {
                throw new AudioDecodingException(AudioDecodingException.Code.DECODE_FAILED, safeDecodeMessage(stderr));
            }
            if (!Files.isRegularFile(output)) {
                throw new AudioDecodingException(AudioDecodingException.Code.DECODE_FAILED, "FFmpeg did not produce PCM output");
            }
            long outputBytes = Files.size(output);
            if (outputBytes <= 0 || (outputBytes % Short.BYTES) != 0) {
                throw new AudioDecodingException(AudioDecodingException.Code.INVALID_PCM, "Decoded PCM is empty or has an invalid byte length");
            }
            if (outputBytes > remainingOutputBytes || outputBytes > properties.getMaxOutputBytes()) {
                throw new AudioDecodingException(AudioDecodingException.Code.OUTPUT_TOO_LARGE, "Decoded PCM exceeds the configured byte limit");
            }
            byte[] pcm = Files.readAllBytes(output);
            short[] samples = new short[pcm.length / Short.BYTES];
            for (int index = 0; index < samples.length; index++) {
                int low = pcm[index * 2] & 0xFF;
                int high = pcm[index * 2 + 1] << 8;
                samples[index] = (short) (low | high);
            }
            return samples;
        } catch (AudioDecodingException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new AudioDecodingException(AudioDecodingException.Code.DECODE_FAILED, "FFmpeg audio decoding failed", exception);
        } finally {
            terminate(process);
            if (stderrFuture != null && !stderrFuture.isDone()) {
                stderrFuture.cancel(true);
            }
            deleteRecursively(workDirectory);
        }
    }

    private void writeTemporaryInput(AudioCaptureSegment segment, Path input) throws IOException {
        if (segment.hasBytes()) {
            Files.write(input, segment.bytes());
        } else {
            Files.copy(segment.path(), input, StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.size(input) > properties.getMaxInputBytesPerSegment()) {
            throw new AudioDecodingException(AudioDecodingException.Code.INPUT_TOO_LARGE, "Audio segment exceeds the configured input limit");
        }
    }

    private void waitForDecode(Process process, Path output, long remainingOutputBytes) {
        long deadline = System.nanoTime() + properties.getDecodeTimeout().toNanos();
        try {
            while (!process.waitFor(25, TimeUnit.MILLISECONDS)) {
                if (Files.exists(output) && Files.size(output) > remainingOutputBytes) {
                    terminate(process);
                    throw new AudioDecodingException(AudioDecodingException.Code.OUTPUT_TOO_LARGE, "FFmpeg output exceeded the configured limit");
                }
                if (System.nanoTime() >= deadline) {
                    terminate(process);
                    throw new AudioDecodingException(AudioDecodingException.Code.DECODE_TIMEOUT, "FFmpeg audio decoding timed out");
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            terminate(process);
            throw new AudioDecodingException(AudioDecodingException.Code.DECODE_TIMEOUT, "FFmpeg audio decoding was interrupted", exception);
        } catch (IOException exception) {
            terminate(process);
            throw new AudioDecodingException(AudioDecodingException.Code.DECODE_FAILED, "FFmpeg output cannot be inspected", exception);
        }
    }

    private AudioDecoderHealth inspectExecutable() {
        if (!properties.isEnabled()) {
            return AudioDecoderHealth.unavailable("disabled", "VAD is disabled");
        }
        String configuredPath = properties.getFfmpegPath();
        if (configuredPath == null || configuredPath.isBlank()) {
            return AudioDecoderHealth.unavailable("path_missing", "An absolute FFmpeg path is not configured");
        }

        Path candidate;
        try {
            candidate = Path.of(configuredPath).normalize();
        } catch (RuntimeException exception) {
            return AudioDecoderHealth.unavailable("path_invalid", "The configured FFmpeg path is invalid");
        }
        if (!candidate.isAbsolute()) {
            return AudioDecoderHealth.unavailable("path_not_absolute", "The configured FFmpeg path must be absolute");
        }
        if (!Files.isRegularFile(candidate) || !Files.isExecutable(candidate)) {
            return AudioDecoderHealth.unavailable("not_executable", "The configured FFmpeg executable is missing or not executable");
        }

        Process process = null;
        try {
            process = new ProcessBuilder(candidate.toString(), "-version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(properties.getHealthCheckTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                terminate(process);
                return AudioDecoderHealth.unavailable("version_timeout", "FFmpeg version check timed out");
            }
            if (process.exitValue() != 0) {
                return AudioDecoderHealth.unavailable("version_failed", "FFmpeg version check failed");
            }
            executable = candidate;
            return AudioDecoderHealth.available("FFmpeg executable and version command are available");
        } catch (IOException exception) {
            return AudioDecoderHealth.unavailable("start_failed", "FFmpeg version command could not be started");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return AudioDecoderHealth.unavailable("version_interrupted", "FFmpeg version check was interrupted");
        } finally {
            terminate(process);
        }
    }

    private String drain(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream captured = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                int writable = Math.min(read, STDERR_CAPTURE_BYTES - captured.size());
                if (writable > 0) {
                    captured.write(buffer, 0, writable);
                }
            }
            return captured.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private String getStderr(Future<String> stderrFuture) {
        try {
            return stderrFuture.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException exception) {
            return "";
        }
    }

    private String safeDecodeMessage(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return "FFmpeg could not decode the audio segment";
        }
        String firstLine = stderr.lines().findFirst().orElse("FFmpeg could not decode the audio segment").trim();
        return firstLine.length() <= 240 ? firstLine : firstLine.substring(0, 240);
    }

    private String normalizeMimeType(String mimeType) {
        int parameters = mimeType.indexOf(';');
        String normalized = parameters >= 0 ? mimeType.substring(0, parameters) : mimeType;
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private void terminate(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(500, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A later OS cleanup can remove a file still held by a terminated native process.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup; callers never receive or persist these temporary paths.
        }
    }

    @PreDestroy
    public void shutdown() {
        stderrExecutor.shutdownNow();
    }
}
