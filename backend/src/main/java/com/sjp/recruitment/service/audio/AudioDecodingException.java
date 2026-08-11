package com.sjp.recruitment.service.audio;

public class AudioDecodingException extends RuntimeException {

    private final Code code;

    public AudioDecodingException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public AudioDecodingException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        EMPTY_INPUT,
        INVALID_SEQUENCE,
        EMPTY_SEGMENT,
        UNSUPPORTED_MIME_TYPE,
        INPUT_TOO_LARGE,
        INVALID_SOURCE,
        DECODER_UNAVAILABLE,
        DECODE_TIMEOUT,
        DECODE_FAILED,
        OUTPUT_TOO_LARGE,
        OUTPUT_TOO_LONG,
        INVALID_PCM
    }
}
