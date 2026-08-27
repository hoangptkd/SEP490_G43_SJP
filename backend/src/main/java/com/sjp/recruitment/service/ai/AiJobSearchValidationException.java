package com.sjp.recruitment.service.ai;

public class AiJobSearchValidationException extends RuntimeException {
    private final String code;
    private final String path;

    // Only application-defined diagnostics belong here, never provider output or CV/JD text.
    public AiJobSearchValidationException(String code, String path, String message) {
        super(message);
        this.code = code;
        this.path = path;
    }

    public String getCode() { return code; }
    public String getPath() { return path; }
}
