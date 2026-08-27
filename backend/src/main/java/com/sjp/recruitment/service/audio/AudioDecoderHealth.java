package com.sjp.recruitment.service.audio;

public record AudioDecoderHealth(boolean available, String code, String detail) {

    public static AudioDecoderHealth available(String detail) {
        return new AudioDecoderHealth(true, "available", detail);
    }

    public static AudioDecoderHealth unavailable(String code, String detail) {
        return new AudioDecoderHealth(false, code, detail);
    }
}
