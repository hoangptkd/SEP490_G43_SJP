package com.sjp.recruitment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.vad")
@Data
public class VadProperties {

    public static final int SAMPLE_RATE = 16_000;
    public static final int FRAME_SAMPLES = 512;
    public static final int CONTEXT_SAMPLES = 64;

    private boolean enabled = true;
    private boolean required;
    private String ffmpegPath = "";
    private Duration decodeTimeout = Duration.ofSeconds(20);
    private Duration healthCheckTimeout = Duration.ofSeconds(3);
    private long maxInputBytesPerSegment = 25L * 1024L * 1024L;
    private long maxOutputBytes = 5_760_000L;
    private int maxDurationSeconds = 180;
    private int maxConcurrentAnalyses = 2;
    private Duration concurrencyAcquireTimeout = Duration.ofSeconds(2);
    private Model model = new Model();
    private Segmentation segmentation = new Segmentation();

    @Data
    public static class Model {
        private String resource = "classpath:models/silero-vad/v6.2.1/silero_vad.onnx";
        private String version = "v6.2.1";
        private String sha256 = "1A153A22F4509E292A94E67D6F9B85E8DEB25B4988682B7E174C65279D8788E3";
    }

    @Data
    public static class Segmentation {
        // These are externally configurable calibration baselines, not fixed production decisions.
        // minSilenceDurationMs is VAD hysteresis. It is not conversationSilenceTimeout
        // and it is not a future business-level fluencyPauseThreshold.
        private float speechThreshold = 0.50f;
        private float negativeThreshold = 0.35f;
        private int minSpeechDurationMs = 250;
        private int minSilenceDurationMs = 100;
        private int speechPadMs = 30;
    }
}
