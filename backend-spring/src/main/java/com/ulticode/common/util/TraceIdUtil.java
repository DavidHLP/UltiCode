package com.ulticode.common.util;

/**
 * Generates request-scoped trace IDs in the project's standard {@code t-<epochMillis>} format.
 *
 * <p>Used by:</p>
 * <ul>
 *   <li>{@code Result.generateTraceId()}</li>
 *   <li>{@code BusinessException.generateTraceId()}</li>
 *   <li>{@code GlobalExceptionHandler} (7 sites)</li>
 *   <li>Controllers that synthesize error responses directly
 *       (e.g. {@code AuditController.exportAuditLogs})</li>
 * </ul>
 *
 * <p>Centralizing the format here lets us change the trace ID scheme in one place
 * if/when the project migrates to a real distributed tracing system.</p>
 */
public final class TraceIdUtil {

    /** Prefix used by every trace ID in the project. Visible for tests. */
    public static final String PREFIX = "t-";

    private TraceIdUtil() {
    }

    /**
     * Generate a fresh trace ID using the current wall-clock time in milliseconds.
     *
     * @return a string of the form {@code t-1718000000000}
     */
    public static String current() {
        return PREFIX + System.currentTimeMillis();
    }
}
