package com.sjp.recruitment.service.ai;

import java.util.ArrayList;
import java.util.List;

/** Bounded, contiguous source excerpts to help the provider copy rather than paraphrase. */
final class AiJobSearchEvidenceQuotes {
    private static final int MAX_LENGTH = 180;

    private AiJobSearchEvidenceQuotes() {}

    static List<String> options(int limitPerSource, String... sources) {
        List<String> result = new ArrayList<>();
        for (String source : sources) {
            if (source == null || limitPerSource <= 0) continue;
            int count = 0;
            String normalized = source.replaceAll("[\\p{Z}\\s]+", " ").trim();
            for (String sentence : normalized.split("(?<=[.!?;]) +")) {
                String remaining = sentence;
                while (remaining.length() >= 5 && count < limitPerSource) {
                    int end = Math.min(MAX_LENGTH, remaining.length());
                    if (end < remaining.length()) {
                        int space = remaining.lastIndexOf(' ', end);
                        if (space >= 5) end = space;
                    }
                    String quote = remaining.substring(0, end).trim();
                    if (quote.length() >= 5) {
                        result.add(quote);
                        count++;
                    }
                    remaining = remaining.substring(end).trim();
                }
                if (count >= limitPerSource) break;
            }
        }
        return List.copyOf(result);
    }
}
