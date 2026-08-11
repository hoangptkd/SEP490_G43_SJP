package com.sjp.recruitment.service.audio;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public final class AudioCaptureSegment {

    private final int sequence;
    private final byte[] bytes;
    private final Path path;
    private final String mimeType;

    private AudioCaptureSegment(int sequence, byte[] bytes, Path path, String mimeType) {
        this.sequence = sequence;
        this.bytes = bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
        this.path = path;
        this.mimeType = Objects.requireNonNullElse(mimeType, "").trim();
    }

    public static AudioCaptureSegment fromBytes(int sequence, byte[] bytes, String mimeType) {
        return new AudioCaptureSegment(sequence, bytes, null, mimeType);
    }

    public static AudioCaptureSegment fromPath(int sequence, Path path, String mimeType) {
        return new AudioCaptureSegment(sequence, null, path, mimeType);
    }

    public int sequence() {
        return sequence;
    }

    public byte[] bytes() {
        return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }

    public Path path() {
        return path;
    }

    public String mimeType() {
        return mimeType;
    }

    public boolean hasBytes() {
        return bytes != null;
    }

    public boolean hasPath() {
        return path != null;
    }
}
