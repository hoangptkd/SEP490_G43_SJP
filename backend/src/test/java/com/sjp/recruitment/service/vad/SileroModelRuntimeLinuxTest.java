package com.sjp.recruitment.service.vad;

import com.sjp.recruitment.config.VadProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class SileroModelRuntimeLinuxTest {

    @Test
    void loadsPinnedModelInspectsContractAndRunsInference() {
        VadProperties properties = new VadProperties();
        SileroModelRuntime runtime = new SileroModelRuntime(properties, new DefaultResourceLoader());
        runtime.initialize();
        try {
            assertTrue(runtime.health().available(), runtime.health().detail());
            SileroModelContract contract = runtime.health().contract();
            assertEquals(java.util.List.of("input", "sr", "state"), contract.inputNames());
            assertEquals(java.util.List.of("output", "stateN"), contract.outputNames());

            VadInferenceOutput output = runtime.infer(new float[576], new float[256]);
            assertTrue(Float.isFinite(output.speechProbability()));
            assertEquals(256, output.recurrentState().length);
        } finally {
            runtime.close();
        }
    }
}
