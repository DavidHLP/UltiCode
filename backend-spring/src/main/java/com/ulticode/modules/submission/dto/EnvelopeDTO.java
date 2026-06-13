package com.ulticode.modules.submission.dto;

import java.util.List;
import java.util.Map;

/**
 * D-form harness envelope as written to stdout by the pre-compiled harness
 * in the sandbox image. Contract:
 *
 * <pre>{@code
 * {
 *   "harness_version": "1.0",
 *   "language": "java",
 *   "exit_code": 0,
 *   "total_elapsed_ms": 42,
 *   "results": [PerCaseResultDTO, ...]
 * }
 * }</pre>
 *
 * <p>{@code exit_code == 0} means the envelope itself is well-formed and
 * per-case verdicts are trustworthy. {@code exit_code == 2} (from the
 * harness) means the harness itself panicked before it could emit a
 * per-case verdict (e.g. parse error on input.json, ambiguous Solution
 * method, javac failure). The backend should fall back to a system-level
 * Runtime Error in that case.
 */
public record EnvelopeDTO(
        String harnessVersion,
        String language,
        int exitCode,
        long totalElapsedMs,
        List<PerCaseResultDTO> results
) {
    @SuppressWarnings("unchecked")
    public static EnvelopeDTO fromMap(Map<String, Object> m) {
        if (m == null) {
            throw new IllegalArgumentException("Envelope map is null");
        }
        int exitCode = m.get("exit_code") instanceof Number n ? n.intValue() : -1;
        long totalElapsed = m.get("total_elapsed_ms") instanceof Number n ? n.longValue() : 0L;
        Object rs = m.get("results");
        List<PerCaseResultDTO> parsed = List.of();
        if (rs instanceof List<?> list) {
            parsed = list.stream()
                    .filter(e -> e instanceof Map<?, ?>)
                    .map(e -> PerCaseResultDTO.fromMap((Map<String, Object>) e))
                    .toList();
        }
        return new EnvelopeDTO(
                strOrNull(m.get("harness_version")),
                strOrNull(m.get("language")),
                exitCode,
                totalElapsed,
                parsed);
    }

    private static String strOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
