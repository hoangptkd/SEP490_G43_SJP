package com.sjp.recruitment.service.vad;

import com.sjp.recruitment.config.VadProperties;
import com.sjp.recruitment.service.audio.AudioDecoder;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("vadHealthIndicator")
public class VadHealthIndicator implements HealthIndicator {

    private final VadProperties properties;
    private final AudioDecoder decoder;
    private final SileroModelRuntime model;

    public VadHealthIndicator(VadProperties properties, AudioDecoder decoder, SileroModelRuntime model) {
        this.properties = properties;
        this.decoder = decoder;
        this.model = model;
    }

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.up().withDetail("enabled", false).build();
        }
        boolean available = decoder.health().available() && model.health().available();
        Health.Builder builder = available ? Health.up() : Health.status("DEGRADED");
        return builder
                .withDetail("enabled", true)
                .withDetail("required", properties.isRequired())
                .withDetail("decoder", decoder.health().code())
                .withDetail("model", model.health().code())
                .withDetail("modelVersion", model.modelVersion())
                .build();
    }
}
