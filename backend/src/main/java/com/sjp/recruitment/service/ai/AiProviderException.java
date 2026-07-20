package com.sjp.recruitment.service.ai;

public class AiProviderException extends RuntimeException {
    private final String code;

    public AiProviderException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
