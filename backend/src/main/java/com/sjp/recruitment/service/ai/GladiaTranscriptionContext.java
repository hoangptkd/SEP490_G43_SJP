package com.sjp.recruitment.service.ai;

import java.util.List;

public record GladiaTranscriptionContext(List<String> vocabulary) {
    public GladiaTranscriptionContext {
        vocabulary = vocabulary == null ? List.of() : List.copyOf(vocabulary);
    }

    public static GladiaTranscriptionContext empty() {
        return new GladiaTranscriptionContext(List.of());
    }
}
