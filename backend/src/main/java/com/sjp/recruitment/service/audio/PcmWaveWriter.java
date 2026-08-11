package com.sjp.recruitment.service.audio;

import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class PcmWaveWriter {
    public Path write(DecodedPcmAudio pcm, Path target) {
        if (pcm == null || pcm.sampleCount() == 0) {
            throw new IllegalArgumentException("PCM audio is required");
        }
        int dataBytes = Math.multiplyExact(pcm.sampleCount(), Short.BYTES);
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) {
            output.write(new byte[]{'R', 'I', 'F', 'F'});
            writeInt(output, 36 + dataBytes);
            output.write(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
            writeInt(output, 16);
            writeShort(output, 1);
            writeShort(output, 1);
            writeInt(output, pcm.sampleRate());
            writeInt(output, pcm.sampleRate() * Short.BYTES);
            writeShort(output, Short.BYTES);
            writeShort(output, 16);
            output.write(new byte[]{'d', 'a', 't', 'a'});
            writeInt(output, dataBytes);
            for (short sample : pcm.samples()) writeShort(output, sample);
            return target;
        } catch (IOException exception) {
            throw new AudioDecodingException(AudioDecodingException.Code.DECODE_FAILED,
                    "Normalized WAV could not be created", exception);
        }
    }

    private void writeInt(OutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    private void writeShort(OutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }
}
