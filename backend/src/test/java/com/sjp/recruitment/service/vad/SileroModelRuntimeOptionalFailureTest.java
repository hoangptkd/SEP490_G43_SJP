package com.sjp.recruitment.service.vad;

import com.sjp.recruitment.config.VadProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SileroModelRuntimeOptionalFailureTest {

    @Test
    void missingModelDegradesWhenVadIsNotRequired() {
        VadProperties properties = new VadProperties();
        properties.setRequired(false);
        properties.getModel().setResource("classpath:models/silero-vad/missing.onnx");
        SileroModelRuntime runtime = new SileroModelRuntime(properties, new DefaultResourceLoader());

        runtime.initialize();
        try {
            assertFalse(runtime.health().available());
            assertEquals("model_load_failed", runtime.health().code());
        } finally {
            runtime.close();
        }
    }
}
