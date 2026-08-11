package com.sjp.recruitment.service.vad;

public record SileroModelHealth(boolean available, String code, String detail, SileroModelContract contract) {

    static SileroModelHealth available(SileroModelContract contract) {
        return new SileroModelHealth(true, "available", "Silero ONNX model is loaded and its contract is valid", contract);
    }

    static SileroModelHealth unavailable(String code, String detail) {
        return new SileroModelHealth(false, code, detail, null);
    }
}
