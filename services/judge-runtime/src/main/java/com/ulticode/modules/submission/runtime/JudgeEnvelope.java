package com.ulticode.modules.submission.runtime;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** D-form harness envelope decoded inside Judge runtime. */
public record JudgeEnvelope(
        String harnessVersion,
        String language,
        int exitCode,
        long totalElapsedMs,
        List<PerCaseResult> results) implements Serializable {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unchecked")
    public static JudgeEnvelope fromMap(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException("Envelope map is null");
        }
        Object rawResults = map.get("results");
        List<PerCaseResult> results = rawResults instanceof List<?> values
                ? values.stream()
                .filter(value -> value instanceof Map<?, ?>)
                .map(value -> PerCaseResult.fromMap((Map<String, Object>) value))
                .toList()
                : List.of();
        return new JudgeEnvelope(
                stringOrNull(map.get("harness_version")),
                stringOrNull(map.get("language")),
                map.get("exit_code") instanceof Number value ? value.intValue() : -1,
                map.get("total_elapsed_ms") instanceof Number value ? value.longValue() : 0L,
                results);
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
