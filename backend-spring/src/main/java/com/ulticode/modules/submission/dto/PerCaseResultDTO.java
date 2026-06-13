package com.ulticode.modules.submission.dto;

import java.util.List;
import java.util.Map;

/**
 * Per-case verdict block as emitted by the D-form harness envelope.
 * Mirrors the JSON written by {@code Main.runCase} / {@code main._run_case}
 * onto the per-case {@code results[]} array of the envelope.
 *
 * <p>Verdict status strings: {@code Accepted}, {@code Wrong Answer},
 * {@code Runtime Error}, {@code Time Limit Exceeded}, {@code Compile Error}.
 * Anything else is treated as Runtime Error by the backend.
 */
public record PerCaseResultDTO(
        String caseId,
        String label,
        long elapsedMs,
        String status,
        Object result,
        Boolean interrupted,
        ErrorDTO error,
        String userStdout,
        String userStderr
) {
    public record ErrorDTO(String type, String message, List<String> stack) {}

    /**
     * Tolerate alternate key spellings (snake_case from any non-Java
     * caller, camelCase from the harness itself).
     */
    @SuppressWarnings("unchecked")
    public static PerCaseResultDTO fromMap(Map<String, Object> m) {
        if (m == null) return null;
        String caseId = strOrNull(m.get("case_id"));
        String label = strOrNull(m.get("label"));
        long elapsedMs = longOrZero(m.get("elapsed_ms"));
        String status = strOrNull(m.get("status"));
        Object result = m.get("result");
        Boolean interrupted = m.get("interrupted") instanceof Boolean b ? b : null;
        Object errObj = m.get("error");
        ErrorDTO err = null;
        if (errObj instanceof Map<?, ?> em) {
            Map<String, Object> em2 = (Map<String, Object>) em;
            err = new ErrorDTO(
                    strOrNull(em2.get("type")),
                    strOrNull(em2.get("message")),
                    em2.get("stack") instanceof List<?> sl
                            ? ((List<Object>) sl).stream().map(String::valueOf).toList()
                            : List.of());
        }
        return new PerCaseResultDTO(
                caseId, label, elapsedMs, status, result, interrupted,
                err,
                strOrNull(m.get("user_stdout")),
                strOrNull(m.get("user_stderr")));
    }

    private static String strOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static long longOrZero(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) { return 0L; }
        }
        return 0L;
    }
}
