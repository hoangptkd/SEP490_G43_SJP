package com.sjp.recruitment.service.vad;

public class VadAnalysisException extends RuntimeException {

    private final String code;

    public VadAnalysisException(String code, String message) {
        super(message);
        this.code = code;
    }

    public VadAnalysisException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
