package com.sjp.recruitment.service.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PcmWaveWriterTest {
    @TempDir Path tempDirectory;

    @Test
    void writesMonoPcm16KhzWaveForWholeMultiSegmentAnswer() throws Exception {
        Path target = tempDirectory.resolve("answer.wav");
        new PcmWaveWriter().write(new DecodedPcmAudio(new short[]{0, 32767, -32768}, 16_000), target);
        byte[] bytes = Files.readAllBytes(target);

        assertThat(new String(bytes, 0, 4)).isEqualTo("RIFF");
        assertThat(new String(bytes, 8, 4)).isEqualTo("WAVE");
        assertThat(bytes).hasSize(44 + 3 * Short.BYTES);
        assertThat(bytes[22]).isEqualTo((byte) 1);
        assertThat(bytes[24] & 0xff).isEqualTo(0x80);
        assertThat(bytes[25] & 0xff).isEqualTo(0x3e);
    }

    @Test
    void createsEquivalentInMemoryWave() throws Exception {
        PcmWaveWriter writer = new PcmWaveWriter();
        DecodedPcmAudio pcm = new DecodedPcmAudio(new short[]{0, 32767, -32768}, 16_000);
        Path target = tempDirectory.resolve("answer-memory-check.wav");

        writer.write(pcm, target);

        assertThat(writer.toByteArray(pcm)).isEqualTo(Files.readAllBytes(target));
    }
}
