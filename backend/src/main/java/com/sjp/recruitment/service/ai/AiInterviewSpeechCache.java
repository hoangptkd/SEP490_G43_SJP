package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.config.AiInterviewProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiInterviewSpeechCache {

    private final AiInterviewProperties properties;
    private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();

    public AiInterviewSpeechCache(AiInterviewProperties properties) {
        this.properties = properties;
    }

    public String key(String normalizedText) {
        String value = String.join("\n",
                properties.getTtsModel(),
                properties.getTtsVoice(),
                properties.getTtsVoiceInstruction(),
                normalizedText);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public byte[] get(String key) {
        CacheEntry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtMillis() <= Instant.now().toEpochMilli()) {
            entries.remove(key, entry);
            return null;
        }
        return entry.audio().clone();
    }

    public byte[] getSpeech(String speechText) {
        String normalized = normalizeSpeech(speechText);
        if (normalized.isBlank()) return null;
        String fullKey = key(normalized);
        byte[] full = get(fullKey);
        if (full != null) return full;

        String[] segments = Arrays.stream(normalized.split("\\R"))
                .map(String::trim)
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
        if (segments.length < 2) return null;
        ByteArrayOutputStream composed = new ByteArrayOutputStream();
        for (String segment : segments) {
            byte[] audio = get(key(segment));
            if (audio == null) return null;
            composed.writeBytes(audio);
        }
        byte[] audio = composed.toByteArray();
        put(fullKey, audio);
        return audio;
    }

    public void put(String key, byte[] audio) {
        if (audio == null || audio.length == 0) {
            return;
        }
        cleanup();
        while (entries.size() >= properties.getTtsCacheMaxEntries()) {
            entries.entrySet().stream()
                    .min(Comparator.comparingLong(value -> value.getValue().createdAtMillis()))
                    .ifPresent(value -> entries.remove(value.getKey(), value.getValue()));
            if (entries.size() >= properties.getTtsCacheMaxEntries() && entries.isEmpty()) {
                break;
            }
        }
        long now = Instant.now().toEpochMilli();
        long expiresAt = now + TimeUnitSeconds.toMillis(properties.getTtsCacheTtlSeconds());
        entries.put(key, new CacheEntry(audio.clone(), now, expiresAt));
    }

    int size() {
        cleanup();
        return entries.size();
    }

    public static String normalizeSpeech(String value) {
        if (value == null) return "";
        return Arrays.stream(value.replace("\r\n", "\n").replace('\r', '\n').split("\n"))
                .map(line -> line.replaceAll("\\s+", " ").trim())
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private void cleanup() {
        long now = Instant.now().toEpochMilli();
        entries.entrySet().removeIf(value -> value.getValue().expiresAtMillis() <= now);
    }

    private record CacheEntry(byte[] audio, long createdAtMillis, long expiresAtMillis) {
    }

    private static final class TimeUnitSeconds {
        private TimeUnitSeconds() {
        }

        private static long toMillis(long seconds) {
            return Math.multiplyExact(seconds, 1_000L);
        }
    }
}
