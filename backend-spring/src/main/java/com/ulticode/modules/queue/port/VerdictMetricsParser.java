package com.ulticode.modules.queue.port;

import org.springframework.stereotype.Component;

/**
 * Wire-string parser for sandbox runtime / memory fields (seam at the queue
 * module's external interface).
 *
 * <p>Before the deepening, the parser lived as two private methods
 * ({@code parseRuntimeMs}, {@code parseMemoryMb}) inside
 * {@code JudgeWorkerProcessor} — the worker that runs the sandbox, but the
 * parser has nothing to do with sandbox execution. The wire format
 * ({@code "123ms"}, {@code "4.2MB"}) is decided by the sandbox adapter; the
 * parser exists to translate from that wire string into typed primitives the
 * rest of the dispatch path can use. Mixing the parser into the worker:
 *
 * <ul>
 *   <li>Leaked the wire string into the hot dispatch path. The worker
 *       repeatedly called {@code parseRuntimeMs(caseResult.getRuntime())}
 *       inside case-reduction loops — 8 callsites total — so any change to
 *       the wire format meant editing the worker, not a small parser.</li>
 *   <li>Made the parser untestable in isolation. Asserting on
 *       {@code parseRuntimeMs("123ms") == 123L} required wiring 18 mocked
 *       collaborators and going through {@code processJob}.</li>
 * </ul>
 *
 * <p>After the deepening, the worker injects this port and calls
 * {@link #parseRuntimeMs(String)} / {@link #parseMemoryMb(String)} directly;
 * the wire format stays in one place.
 *
 * <p><b>Dependency category:</b> in-process (no I/O). No adapter needed.
 *
 * @author ulticode
 */
@Component
public class VerdictMetricsParser {

    /** Sentinel returned when parsing fails. Callers can clamp downstream. */
    public static final long RUNTIME_PARSE_FAILED = 0L;
    public static final double MEMORY_PARSE_FAILED = 0.0;

    /**
     * Parse a runtime wire string (e.g. {@code "123ms"}) to milliseconds.
     * Returns {@link #RUNTIME_PARSE_FAILED} for null / blank input or
     * unparseable values.
     */
    public long parseRuntimeMs(String runtime) {
        if (runtime == null || runtime.isBlank()) {
            return RUNTIME_PARSE_FAILED;
        }
        try {
            return Long.parseLong(stripSuffix(runtime, "ms"));
        } catch (NumberFormatException e) {
            return RUNTIME_PARSE_FAILED;
        }
    }

    /**
     * Parse a memory wire string (e.g. {@code "4.2MB"}) to megabytes.
     * Returns {@link #MEMORY_PARSE_FAILED} for null / blank input or
     * unparseable values.
     */
    public double parseMemoryMb(String memory) {
        if (memory == null || memory.isBlank()) {
            return MEMORY_PARSE_FAILED;
        }
        try {
            return Double.parseDouble(stripSuffix(memory, "MB"));
        } catch (NumberFormatException e) {
            return MEMORY_PARSE_FAILED;
        }
    }

    private static String stripSuffix(String s, String suffix) {
        return s.replace(suffix, "").trim();
    }
}