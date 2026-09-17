package com.ulticode.modules.submission.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.submission.entity.Submission;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Typed view of the persisted judge-outbox payload.
 *
 * <p>Rows keep the {@code Map<String,Object>} column form for stored-JSON
 * compatibility; this record owns the field names, the producer-side shape,
 * and the decode rules — including the legacy double-encoded string form and
 * string-encoded numbers.</p>
 */
public record JudgeOutboxPayload(
        String submissionId,
        String problemId,
        String userId,
        String language,
        String code,
        long generation,
        Integer timeLimitMs,
        Integer memoryLimitKb) {

    /** Builds the producer-side payload written when a dispatch row is created. */
    public static JudgeOutboxPayload forSubmission(
            Submission submission, String problemId, long generation) {
        return new JudgeOutboxPayload(
                submission.getId(),
                problemId,
                submission.getUserId(),
                submission.getLanguage(),
                submission.getCode(),
                generation,
                null,
                null);
    }

    /**
     * Persisted JSON form. Keys and order are a compatibility contract: keep
     * them identical to the rows already stored in {@code judge_outbox}.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("submissionId", submissionId);
        payload.put("problemId", problemId);
        payload.put("userId", userId);
        payload.put("language", language);
        payload.put("code", code);
        payload.put("generation", generation);
        if (timeLimitMs != null) {
            payload.put("timeLimitMs", timeLimitMs);
        }
        if (memoryLimitKb != null) {
            payload.put("memoryLimitKb", memoryLimitKb);
        }
        return payload;
    }

    /**
     * Decodes a persisted payload: a JSON map, or the legacy string form when
     * the column was stored double-encoded or failed structured decoding.
     * Returns empty when the required judge fields (problemId, userId,
     * language, code) are missing or undecodable.
     */
    public static Optional<JudgeOutboxPayload> fromLegacy(Object raw, ObjectMapper objectMapper) {
        Map<String, Object> values = asMap(raw, objectMapper);
        String problemId = text(values, "problemId");
        String userId = text(values, "userId");
        String language = text(values, "language");
        String code = text(values, "code");
        if (!hasText(problemId) || !hasText(userId) || !hasText(language) || !hasText(code)) {
            return Optional.empty();
        }
        return Optional.of(new JudgeOutboxPayload(
                text(values, "submissionId"),
                problemId,
                userId,
                language,
                code,
                longOrZero(values, "generation"),
                intOrNull(values, "timeLimitMs"),
                intOrNull(values, "memoryLimitKb")));
    }

    private static Map<String, Object> asMap(Object raw, ObjectMapper objectMapper) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> values = new HashMap<>();
            map.forEach((key, value) -> {
                if (key instanceof String textKey) {
                    values.put(textKey, value);
                }
            });
            return values;
        }
        if (raw instanceof String json && !json.isBlank()) {
            try {
                Map<String, Object> parsed =
                        objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
                        });
                return parsed == null ? Map.of() : parsed;
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private static String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }

    private static long longOrZero(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static Integer intOrNull(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
