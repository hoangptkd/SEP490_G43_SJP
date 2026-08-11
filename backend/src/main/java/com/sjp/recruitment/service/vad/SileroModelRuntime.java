package com.sjp.recruitment.service.vad;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.sjp.recruitment.config.VadProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SileroModelRuntime implements VadModel, AutoCloseable {

    private static final Set<String> EXPECTED_INPUTS = Set.of("input", "state", "sr");
    private static final Set<String> EXPECTED_OUTPUTS = Set.of("output", "stateN");
    private static final int RECURRENT_STATE_VALUES = 2 * 1 * 128;

    private final VadProperties properties;
    private final ResourceLoader resourceLoader;
    private volatile OrtEnvironment environment;
    private volatile OrtSession session;
    private volatile SileroModelHealth health = SileroModelHealth.unavailable("not_loaded", "Silero model has not been loaded");

    public SileroModelRuntime(VadProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    void initialize() {
        if (!properties.isEnabled()) {
            health = SileroModelHealth.unavailable("disabled", "VAD is disabled");
            return;
        }
        try {
            byte[] modelBytes = loadAndVerifyModel();
            environment = OrtEnvironment.getEnvironment();
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setInterOpNumThreads(1);
                options.setIntraOpNumThreads(1);
                options.setDeterministicCompute(true);
                options.addCPU(true);
                OrtSession candidate = environment.createSession(modelBytes, options);
                boolean accepted = false;
                try {
                    SileroModelContract contract = inspectAndValidate(candidate);
                    validateDryRun(candidate, environment);
                    session = candidate;
                    accepted = true;
                    health = SileroModelHealth.available(contract);
                } finally {
                    if (!accepted) {
                        candidate.close();
                    }
                }
            }
        } catch (ModelContractException exception) {
            closeSessionQuietly();
            health = SileroModelHealth.unavailable("model_contract_mismatch", safeDetail(exception));
            if (properties.isRequired()) {
                throw new IllegalStateException("Required Silero VAD model contract is invalid", exception);
            }
        } catch (IOException | OrtException | NoSuchAlgorithmException | RuntimeException | LinkageError exception) {
            closeSessionQuietly();
            health = SileroModelHealth.unavailable("model_load_failed", safeDetail(exception));
            if (properties.isRequired()) {
                throw new IllegalStateException("Required Silero VAD model could not be loaded", exception);
            }
        }
    }

    @Override
    public VadInferenceOutput infer(float[] inputWithContext, float[] recurrentState) {
        OrtSession currentSession = session;
        OrtEnvironment currentEnvironment = environment;
        if (!health.available() || currentSession == null || currentEnvironment == null) {
            throw new VadAnalysisException("model_unavailable", "Silero VAD model is unavailable");
        }
        if (inputWithContext == null || inputWithContext.length != VadProperties.CONTEXT_SAMPLES + VadProperties.FRAME_SAMPLES) {
            throw new VadAnalysisException("invalid_input_shape", "Silero input must contain 576 float samples");
        }
        if (recurrentState == null || recurrentState.length != RECURRENT_STATE_VALUES) {
            throw new VadAnalysisException("invalid_state_shape", "Silero recurrent state must contain 256 float values");
        }

        try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                currentEnvironment,
                FloatBuffer.wrap(inputWithContext),
                new long[]{1, inputWithContext.length}
        ); OnnxTensor stateTensor = OnnxTensor.createTensor(
                currentEnvironment,
                FloatBuffer.wrap(recurrentState),
                new long[]{2, 1, 128}
        ); OnnxTensor sampleRateTensor = OnnxTensor.createTensor(
                currentEnvironment,
                LongBuffer.wrap(new long[]{VadProperties.SAMPLE_RATE}),
                new long[]{1}
        ); OrtSession.Result result = currentSession.run(Map.of(
                "input", inputTensor,
                "state", stateTensor,
                "sr", sampleRateTensor
        ))) {
            OnnxTensor probabilityTensor = requireTensor(result, "output");
            OnnxTensor nextStateTensor = requireTensor(result, "stateN");
            float probability = probabilityTensor.getFloatBuffer().get(0);
            if (!Float.isFinite(probability)) {
                throw new VadAnalysisException("invalid_probability", "Silero returned a non-finite speech probability");
            }
            float[] nextState = new float[RECURRENT_STATE_VALUES];
            nextStateTensor.getFloatBuffer().get(nextState);
            return new VadInferenceOutput(probability, nextState);
        } catch (OrtException exception) {
            throw new VadAnalysisException("inference_failed", "Silero ONNX inference failed", exception);
        }
    }

    @Override
    public SileroModelHealth health() {
        return health;
    }

    @Override
    public String modelVersion() {
        return properties.getModel().getVersion();
    }

    private byte[] loadAndVerifyModel() throws IOException, NoSuchAlgorithmException {
        Resource resource = resourceLoader.getResource(properties.getModel().getResource());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IOException("Configured Silero model resource is missing or unreadable");
        }
        byte[] bytes;
        try (var input = resource.getInputStream()) {
            bytes = input.readAllBytes();
        }
        String actualChecksum = HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)
        );
        if (!actualChecksum.equalsIgnoreCase(properties.getModel().getSha256())) {
            throw new IOException("Silero model checksum does not match pinned metadata");
        }
        return bytes;
    }

    private SileroModelContract inspectAndValidate(OrtSession candidate) throws OrtException {
        if (!candidate.getInputNames().equals(EXPECTED_INPUTS)) {
            throw new ModelContractException("Silero input names do not match the expected contract");
        }
        if (!candidate.getOutputNames().equals(EXPECTED_OUTPUTS)) {
            throw new ModelContractException("Silero output names do not match the expected contract");
        }

        Map<String, NodeInfo> inputs = candidate.getInputInfo();
        Map<String, NodeInfo> outputs = candidate.getOutputInfo();
        TensorInfo input = requireTensorInfo(inputs, "input", OnnxJavaType.FLOAT);
        TensorInfo state = requireTensorInfo(inputs, "state", OnnxJavaType.FLOAT);
        TensorInfo sampleRate = requireTensorInfo(inputs, "sr", OnnxJavaType.INT64);
        TensorInfo output = requireTensorInfo(outputs, "output", OnnxJavaType.FLOAT);
        TensorInfo nextState = requireTensorInfo(outputs, "stateN", OnnxJavaType.FLOAT);

        requireShape("input", input.getShape(), 2, new long[]{-1, -1});
        requireShape("state", state.getShape(), 3, new long[]{2, -1, 128});
        requireSampleRateShape(sampleRate.getShape());
        requireShape("output", output.getShape(), 2, new long[]{-1, 1});
        requireShape("stateN", nextState.getShape(), 3, new long[]{2, -1, 128});

        return new SileroModelContract(
                candidate.getInputNames().stream().sorted().toList(),
                candidate.getOutputNames().stream().sorted().toList(),
                Arrays.toString(input.getShape()),
                Arrays.toString(state.getShape()),
                Arrays.toString(sampleRate.getShape()),
                Arrays.toString(output.getShape()),
                Arrays.toString(nextState.getShape())
        );
    }

    private TensorInfo requireTensorInfo(Map<String, NodeInfo> nodes, String name, OnnxJavaType expectedType) {
        NodeInfo node = nodes.get(name);
        if (node == null || !(node.getInfo() instanceof TensorInfo tensorInfo)) {
            throw new ModelContractException("Silero node is missing or is not a tensor: " + name);
        }
        if (tensorInfo.type != expectedType) {
            throw new ModelContractException("Silero tensor type mismatch for " + name);
        }
        return tensorInfo;
    }

    private void requireShape(String name, long[] actual, int rank, long[] expected) {
        if (actual.length != rank) {
            throw new ModelContractException("Silero tensor rank mismatch for " + name + ": " + Arrays.toString(actual));
        }
        for (int index = 0; index < expected.length; index++) {
            long expectedDimension = expected[index];
            long actualDimension = actual[index];
            if (expectedDimension >= 0 && actualDimension != -1 && actualDimension != expectedDimension) {
                throw new ModelContractException("Silero tensor shape mismatch for " + name + ": " + Arrays.toString(actual));
            }
            if (expectedDimension < 0 && actualDimension == 0) {
                throw new ModelContractException("Silero tensor contains an invalid zero dimension for " + name + ": " + Arrays.toString(actual));
            }
        }
    }

    private void requireSampleRateShape(long[] actual) {
        boolean scalar = actual.length == 0;
        boolean singleValue = actual.length == 1 && (actual[0] == 1 || actual[0] == -1);
        if (!scalar && !singleValue) {
            throw new ModelContractException("Silero sample-rate tensor shape mismatch");
        }
    }

    private OnnxTensor requireTensor(OrtSession.Result result, String name) {
        OnnxValue value = result.get(name)
                .orElseThrow(() -> new VadAnalysisException("missing_output", "Silero output is missing: " + name));
        if (!(value instanceof OnnxTensor tensor)) {
            throw new VadAnalysisException("invalid_output", "Silero output is not a tensor: " + name);
        }
        return tensor;
    }

    private void validateDryRun(OrtSession candidate, OrtEnvironment currentEnvironment) throws OrtException {
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                currentEnvironment,
                FloatBuffer.wrap(new float[VadProperties.CONTEXT_SAMPLES + VadProperties.FRAME_SAMPLES]),
                new long[]{1, VadProperties.CONTEXT_SAMPLES + VadProperties.FRAME_SAMPLES}
        ); OnnxTensor stateTensor = OnnxTensor.createTensor(
                currentEnvironment,
                FloatBuffer.wrap(new float[RECURRENT_STATE_VALUES]),
                new long[]{2, 1, 128}
        ); OnnxTensor sampleRateTensor = OnnxTensor.createTensor(
                currentEnvironment,
                LongBuffer.wrap(new long[]{VadProperties.SAMPLE_RATE}),
                new long[]{1}
        ); OrtSession.Result result = candidate.run(Map.of(
                "input", inputTensor,
                "state", stateTensor,
                "sr", sampleRateTensor
        ))) {
            OnnxTensor output = requireTensor(result, "output");
            OnnxTensor nextState = requireTensor(result, "stateN");
            if (!Arrays.equals(output.getInfo().getShape(), new long[]{1, 1})) {
                throw new ModelContractException(
                        "Silero dry-run output shape mismatch: " + Arrays.toString(output.getInfo().getShape())
                );
            }
            if (!Arrays.equals(nextState.getInfo().getShape(), new long[]{2, 1, 128})) {
                throw new ModelContractException(
                        "Silero dry-run stateN shape mismatch: " + Arrays.toString(nextState.getInfo().getShape())
                );
            }
        }
    }

    private String safeDetail(Throwable exception) {
        String name = exception.getClass().getSimpleName();
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return name;
        }
        String detail = name + ": " + message.lines().findFirst().orElse("");
        return detail.length() <= 300 ? detail : detail.substring(0, 300);
    }

    private void closeSessionQuietly() {
        OrtSession current = session;
        session = null;
        if (current != null) {
            try {
                current.close();
            } catch (OrtException ignored) {
                // The runtime is already degraded and cannot use this session again.
            }
        }
    }

    @Override
    @PreDestroy
    public void close() {
        closeSessionQuietly();
        environment = null;
        // OrtEnvironment is process-wide and may be shared by other ONNX consumers.
    }

    private static final class ModelContractException extends RuntimeException {
        private ModelContractException(String message) {
            super(message);
        }
    }
}
