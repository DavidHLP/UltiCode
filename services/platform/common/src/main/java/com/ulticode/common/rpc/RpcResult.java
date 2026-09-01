package com.ulticode.common.rpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ulticode.common.error.NamespacedErrorCode;
import java.io.Serializable;

import java.util.List;
import java.util.Objects;

/**
 * Stable, transport-agnostic envelope for inter-service (RPC) responses.
 *
 * <p>Distinct from {@code com.ulticode.common.response.Result} on three axes, by
 * design (see {@code docs/architecture/data-flow.md}: RPC uses an independent
 * stable {@code RpcResult} and must not serialize exceptions):
 * <ul>
 *   <li><b>No static trace coupling.</b> {@code Result} auto-stamps
 *       {@code TraceIdUtil.current()} inside its factory methods;
 *       {@code RpcResult} carries {@link #traceId} as an explicit,
 *       caller-supplied field so it can be propagated through
 *       Dubbo/OTel/W3C unchanged. Producer and consumer each run their own
 *       trace id scheme and may differ on prefix.</li>
 *   <li><b>Exception is not serialized.</b> Only the {@link ErrorPayload}
 *       survives the wire &mdash; a plain record carrying
 *       {@code namespace + code + message}. No {@code Throwable}, no
 *       {@code stackTrace}, no {@code cause chain}. The receiving service
 *       decides whether to translate to a typed business exception or
 *       surface a generic fault. {@link ErrorPayload} is a concrete record
 *       (not the {@link NamespacedErrorCode} interface) so the JSON
 *       round-trip is deterministic across services that may not share the
 *       exact same Java class on the other side of the wire.</li>
 *   <li><b>Pagination is first-class on the envelope</b> so every
 *       pagination-bearing RPC provider returns the same shape
 *       instead of nesting {@code page} under a free-form {@code data}.</li>
 * </ul>
 *
 * <p>Wire shape (intentionally JSON-friendly; the {@code data} field is the
 * only generic payload, every other field is a concrete record or scalar):
 * <pre>{@code
 * {
 *   "success":   true,
 *   "data":      null | { ...T... },
 *   "page":      null | { "items": [...], "total": 100, "page": 1, "pageSize": 20, "totalPages": 5 },
 *   "error":     null | { "namespace": "submission", "code": 40003, "message": "Rate limited" },
 *   "traceId":   "t-1718000000000",
 *   "deadlineMs":   null | 1718000005000,
 *   "idempotencyKey": null | "uuid"
 * }
 * }</pre>
 *
 * <p>This is a record so the shape is sealed and consumers can
 * pattern-match on it. Use the {@code success()} / {@code data()} /
 * {@code page()} / {@code error()} component accessors directly; no
 * shadow {@code isSuccess()}/{@code getData()} helpers are provided.
 *
 * @param <T> payload type (only present when {@link #success()} is {@code true}
 *           and {@link #page()} is null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RpcResult<T>(
        boolean success,
        T data,
        Page page,
        ErrorPayload error,
        String traceId,
        Long deadlineMs,
        String idempotencyKey) implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * Pagination sub-payload; null for non-paginated RPCs. The {@code items}
     * element type is a wildcard because Java records cannot reuse the
     * enclosing record's type parameter on a nested declaration.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Page(
            List<?> items,
            Long total,
            Integer page,
            Integer pageSize,
            Integer totalPages) implements Serializable {
        private static final long serialVersionUID = 1L;

    }

    /**
     * JSON-round-trippable error sub-payload.
     *
     * <p>A concrete record (deliberately not the {@link NamespacedErrorCode}
     * interface) so the wire shape is identical regardless of which Java
     * module on the receiving side deserializes the response &mdash; the
     * namespace + code integers are the only stable contract.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorPayload(
            String namespace,
            int code,
            String message) implements Serializable {
        private static final long serialVersionUID = 1L;


        /**
         * @return this payload's namespace, or {@code ""} for
         *         base-error codes (see {@link com.ulticode.common.error.BaseErrorCode}).
         */
        public String namespace() {
            return namespace == null ? "" : namespace;
        }

        /**
         * Project an {@link ErrorPayload} from any {@link NamespacedErrorCode}.
         * Pure data mapping; never throws, never mutates.
         */
        public static ErrorPayload of(NamespacedErrorCode source) {
            Objects.requireNonNull(source, "source");
            String namespace = source.namespace();
            String message = source.message();
            return new ErrorPayload(
                    namespace == null ? "" : namespace,
                    source.code(),
                    message == null ? "" : message);
        }
    }

    /* ===== factory methods ============================================ */

    /** Single-payload success. {@code page} is null. */
    public static <T> RpcResult<T> success(T data, String traceId) {
        return new RpcResult<>(
                true,
                Objects.requireNonNull(data, "data"),
                null,
                null,
                traceId,
                null,
                null);
    }

    /** Single-payload success with deadline + idempotency key. */
    public static <T> RpcResult<T> success(
            T data, String traceId, long deadlineMs, String idempotencyKey) {
        return new RpcResult<>(
                true,
                Objects.requireNonNull(data, "data"),
                null,
                null,
                traceId,
                deadlineMs,
                idempotencyKey);
    }

    /** Unit success (no payload). */
    public static RpcResult<Void> success(String traceId) {
        return new RpcResult<>(true, null, null, null, traceId, null, null);
    }

    /** Paginated success; the page list shape is set explicitly. */
    public static <T> RpcResult<T> page(
            List<T> items, long total, int page, int pageSize, String traceId) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
        Page rpcPage = new Page(items, total, page, pageSize, totalPages);
        return new RpcResult<>(true, null, rpcPage, null, traceId, null, null);
    }

    /** Error result mapping a {@link NamespacedErrorCode} to the wire payload. */
    public static <T> RpcResult<T> failure(NamespacedErrorCode error, String traceId) {
        return new RpcResult<>(
                false,
                null,
                null,
                ErrorPayload.of(Objects.requireNonNull(error, "error")),
                traceId,
                null,
                null);
    }

    /** Error result mapping a {@link NamespacedErrorCode} + deadline. */
    public static <T> RpcResult<T> failure(
            NamespacedErrorCode error, String traceId, long deadlineMs) {
        return new RpcResult<>(
                false,
                null,
                null,
                ErrorPayload.of(Objects.requireNonNull(error, "error")),
                traceId,
                deadlineMs,
                null);
    }

    /** Error result carrying an already-shaped wire payload (consumer side). */
    public static <T> RpcResult<T> failure(ErrorPayload error, String traceId) {
        return new RpcResult<>(
                false,
                null,
                null,
                Objects.requireNonNull(error, "error"),
                traceId,
                null,
                null);
    }
}