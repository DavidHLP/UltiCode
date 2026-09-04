package com.ulticode.modules.submission.runtime;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** Per-case verdict block emitted by the D-form harness. */
public record PerCaseResult(
        String caseId,
        String label,
        long elapsedMs,
        long peakMemoryBytes,
        long elapsedUs,
        long cpuMs,
        String status,
        Object result,
        Boolean interrupted,
        ErrorDTO error,
        String userStdout,
        String userStderr) implements Serializable {

    private static final long serialVersionUID = 1L;

    public record ErrorDTO(String type, String message, List<String> stack)
            implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @SuppressWarnings("unchecked")
    public static PerCaseResult fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        ErrorDTO error = null;
        Object errorValue = map.get("error");
        if (errorValue instanceof Map<?, ?> rawError) {
            Map<String, Object> errorMap = (Map<String, Object>) rawError;
            error = new ErrorDTO(
                    stringOrNull(errorMap.get("type")),
                    stringOrNull(errorMap.get("message")),
                    errorMap.get("stack") instanceof List<?> stack
                            ? stack.stream().map(String::valueOf).toList()
                            : List.of());
        }
        return new PerCaseResult(
                stringOrNull(map.get("case_id")),
                stringOrNull(map.get("label")),
                longOrZero(map.get("elapsed_ms")),
                longOrZero(map.get("peak_memory_bytes")),
                longOrZero(map.get("elapsed_us")),
                longOrZero(map.get("cpu_ms")),
                stringOrNull(map.get("status")),
                map.get("result"),
                map.get("interrupted") instanceof Boolean interrupted ? interrupted : null,
                error,
                stringOrNull(map.get("user_stdout")),
                stringOrNull(map.get("user_stderr")));
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long longOrZero(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
