package com.ulticode.modules.submission.dto;

import java.util.List;
import java.util.Map;

/**
 * Per-case verdict block as emitted by the D-form harness envelope.
 * Mirrors the JSON written by {@code Main.runCase} / {@code main._run_case}
 * onto the per-case {@code results[]} array of the envelope.
 *
 * <p>Verdict status strings: {@code Accepted}, {@code Wrong Answer},
 * {@code Runtime Error}, {@code Time Limit Exceeded}, {@code Compile Error},
 * {@code Memory Limit Exceeded}. Anything else is treated as Runtime Error
 * by the backend.
 *
 * <p>Measurement fields (resource measurement contract, ADR-002 §8):
 * <ul>
 *   <li>{@code elapsedMs} / {@code elapsedUs} — wall-clock duration in
 *       milliseconds (legacy, ms-truncated) and microseconds (precise).
 *       Prefer {@code elapsedUs} for display; {@code elapsedMs} stays for
 *       backwards compatibility.</li>
 *   <li>{@code peakMemoryBytes} — peak resident-set / heap of the case, in
 *       bytes. Semantics differ slightly by language (see ADR-002 §8) but
 *       all report a genuine peak (not a single-point sample).</li>
 *   <li>{@code cpuMs} — CPU time (user + sys) the user code actually
 *       consumed, in milliseconds. Used for fair cross-language comparison;
 *       TLE is still judged on wall-clock.</li>
 * </ul>
 */
public record PerCaseResultDTO(
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
        // peak_memory_bytes is emitted by Main.java since the M3 memory-
        // reporting patch; tolerate older harnesses by defaulting to 0.
        long peakMemoryBytes = longOrZero(m.get("peak_memory_bytes"));
        // Precise wall-clock (microseconds) and CPU time (ms). Newer
        // harnesses emit these; older ones default to 0, in which case
        // callers fall back to elapsedMs.
        long elapsedUs = longOrZero(m.get("elapsed_us"));
        long cpuMs = longOrZero(m.get("cpu_ms"));
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
                caseId, label, elapsedMs, peakMemoryBytes, elapsedUs, cpuMs,
                status, result, interrupted,
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
