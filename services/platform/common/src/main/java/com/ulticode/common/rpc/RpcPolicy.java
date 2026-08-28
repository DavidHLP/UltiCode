package com.ulticode.common.rpc;


/**
 * P4-RPC-002: centralized RPC timeout / retry / idempotency policy constants.
 *
 * <p>Codifies migration guide &sect;6.4 so every Dubbo Consumer reference
 * in the P4-CUTOVER tasks reads from a single source of truth rather than
 * hard-coding magic numbers. The constants are intentionally
 * {@code public static final} so they can be used directly in
 * {@code @DubboReference(timeout = ..., retries = ...)} annotations (annotation
 * attributes require compile-time constants).
 *
 * <h2>Policy matrix</h2>
 * <table border="1">
 * <tr><th>Call type</th><th>Timeout</th><th>Auto-retry</th><th>Idempotency key</th></tr>
 * <tr><td>Write (Command RPC)</td><td>{@value #WRITE_TIMEOUT_MS} ms (3 s)</td>
 *     <td>{@value #WRITE_RETRIES} (no auto-retry)</td><td>Required (enforced by WriteCommand)</td></tr>
 * <tr><td>Query (Read RPC)</td><td>{@value #QUERY_TIMEOUT_MS} ms (800 ms)</td>
 *     <td>{@value #QUERY_RETRIES} (one retry with jitter)</td><td>Not applicable</td></tr>
 * <tr><td>Execution (long-running, non-idempotent)</td><td>{@value #EXECUTION_TIMEOUT_MS} ms</td>
 *     <td>{@value #EXECUTION_RETRIES} (no auto-retry)</td><td>Not applicable</td></tr>
 * </table>
 *
 * <h2>YAML global default = WRITE boundary (fail-safe)</h2>
 * The global Dubbo consumer default in every service module's
 * {@code application.yml} is {@code timeout=3000, retries=0} &mdash; the
 * write-safe boundary. This is deliberate: a write RPC that accidentally
 * inherits the query retry count ({@code retry=1}) can double-apply a
 * side effect (critical correctness bug), whereas a query RPC that
 * accidentally inherits the write retry count ({@code retry=0}) is merely
 * conservative (safe but suboptimal). CUTOVER consumers MUST override
 * query references explicitly:
 * <pre>{@code
 * // Write reference — uses global default (no override needed)
 * @DubboReference // timeout=3000, retries=0 from YAML
 *
 * // Query reference — MUST override
 * @DubboReference(timeout = RpcPolicy.QUERY_TIMEOUT_MS,
 *                 retries = RpcPolicy.QUERY_RETRIES)
 * }</pre>
 *
 * <h2>Rationale (per &sect;6.4)</h2>
 * <ul>
 *   <li><b>Write retry=0:</b> an auto-retried write may double-apply the side
 *       effect. Retries are only safe when the Caller re-sends the same
 *       {@code commandId} explicitly; the framework's automatic retry is
 *       disabled to prevent silent duplication.</li>
 *   <li><b>Query retry=1:</b> a read is safe to retry once; the framework
 *       adds jitter so a cluster-wide retry storm does not synchronise.</li>
 *   <li><b>Timeout ranges:</b> the upper bounds (3 s / 800 ms) match the
 *       guide's p99 guidance; concrete services may tighten them based on
 *       observed p99 in production.</li>
 * </ul>
 *
 * @see com.ulticode.common.tracing.TraceMetadata for deadline propagation
 */
public final class RpcPolicy {

    private RpcPolicy() {
    }

    // ── Write (Command RPC) ──────────────────────────────────────

    /** Upper-bound timeout for mutating RPC calls (3 seconds). */
    public static final int WRITE_TIMEOUT_MS = 3000;

    /** Auto-retry count for mutating RPC calls: zero (no automatic retry). */
    public static final int WRITE_RETRIES = 0;

    // ── Query (Read RPC) ─────────────────────────────────────────

    /** Upper-bound timeout for read-only RPC calls (800 milliseconds). */
    public static final int QUERY_TIMEOUT_MS = 800;

    /** Auto-retry count for read-only RPC calls: one retry (with framework jitter). */
    public static final int QUERY_RETRIES = 1;

    // ── Long-running execution ───────────────────────────────────

    /** Covers the sandbox's 180-second hard cap plus RPC response overhead. */
    public static final int EXECUTION_TIMEOUT_MS = 190_000;

    /** Sandbox execution is never automatically retried. */
    public static final int EXECUTION_RETRIES = 0;
}
