package com.ulticode.modules.queue.outbox.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.submission.api.queue.JudgeJobEnvelope;

import java.util.Map;

/**
 * Translates the Submission-owned outbox representation into the
 * provider-owned Judge envelope.
 *
 * <p>The dispatcher owns delivery state; this package-local seam owns only
 * payload decoding, required-field validation, defaults, and attempt identity.
 */
final class JudgeJobEnvelopeTranslator {

    private static final int DEFAULT_TIME_LIMIT_MS = 2000;
    private static final int DEFAULT_MEMORY_LIMIT_KB = 256 * 1024;

    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;

    JudgeJobEnvelopeTranslator(ObjectMapper objectMapper, UuidGenerator uuidGenerator) {
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
    }

    /**
     * Return {@code null} for an unusable row so the caller can dead-letter it
     * without marking a partially populated envelope as sent.
     */
    JudgeJobEnvelope translate(JudgeOutboxRecord row) {
        Map<String, Object> payload = extractPayload(row);
        if (row == null
                || !hasText(row.getSubmissionId())
                || !hasText(stringOrNull(payload, "problemId"))
                || !hasText(stringOrNull(payload, "userId"))
                || !hasText(stringOrNull(payload, "language"))
                || !hasText(stringOrNull(payload, "code"))) {
            return null;
        }
        return new JudgeJobEnvelope(
                JudgeJobEnvelope.VERSION_2,
                row.getId(),
                row.getSubmissionId(),
                stringOrNull(payload, "problemId"),
                stringOrNull(payload, "userId"),
                stringOrNull(payload, "language"),
                stringOrNull(payload, "code"),
                intOrDefault(payload, "timeLimitMs", DEFAULT_TIME_LIMIT_MS),
                intOrDefault(payload, "memoryLimitKb", DEFAULT_MEMORY_LIMIT_KB),
                row.getGeneration(),
                uuidGenerator.newId());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(JudgeOutboxRecord row) {
        if (row == null) {
            return Map.of();
        }
        Object payloadObj = row.getPayload();
        if (payloadObj instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (payloadObj instanceof String jsonStr && !jsonStr.isBlank()) {
            try {
                return objectMapper.readValue(jsonStr, Map.class);
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String stringOrNull(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    private static int intOrDefault(Map<String, Object> map, String key, int defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
