package com.ulticode.common.error;

/**
 * Base type for module-scoped error codes.
 *
 * <p>Each microservice / module defines its own enum (e.g. {@code AuthErrorCode},
 * {@code UserErrorCode}, {@code SubmissionErrorCode}) that implements this
 * interface. The interface enforces three invariants the backend commits to:
 * <ul>
 *   <li><b>namespace</b> &mdash; a short stable identifier (e.g. {@code "auth"},
 *       {@code "submission"}) used for cross-process routing and
 *       {@code RpcResult} payloads;</li>
 *   <li><b>code</b> &mdash; an integer business code local to the namespace;
 *       callers map {@code namespace + code} to a stable message via the
 *       module's own catalog;</li>
 *   <li><b>message</b> &mdash; a human-readable, English diagnostic string
 *       suitable for surfacing to operators and for the NestJS frontend
 *       contract (legacy HTTP envelope preserved verbatim).</li>
 * </ul>
 *
 * <p>The HTTP status mapping remains the responsibility of each module's
 * {@code ErrorCode} enum and / or handler; backend-common does not know
 * about specific HTTP semantics because doing so would couple the shared
 * contract to a single transport (HTTP), defeating the point of an RPC-safe
 * error type.
 *
 * <p><b>Why an interface, not a base enum</b>: legacy already has a
 * module-private {@code com.ulticode.common.exception.ErrorCode} enum with
 * ~150 business constants and a Spring {@code HttpStatus} mapping. We can
 * not move it to backend-common (it would pull Spring {@code @Component}
 * scanning and business semantics). Instead, {@link NamespacedErrorCode}
 * is the contract every new module's enum implements; legacy's enum can
 * be retro-fitted via a thin adapter during the owner boundary split.
 *
 * <p>Existing {@code Result.error(Integer code, String message, String traceId)}
 * keeps its {@code Integer}-based payload. {@code RpcResult} uses
 * {@link #namespace()} + {@link #code()} pairs to stay lossless across
 * service boundaries.
 */
public interface NamespacedErrorCode {

    /**
     * @return a short, lower-case, stable identifier for the error's owning
     *         module (e.g. {@code "auth"}, {@code "submission"}). Used as
     *         the wire key in {@code RpcResult}.
     */
    String namespace();

    /**
     * @return the integer business code local to {@link #namespace()}.
     */
    int code();

    /**
     * @return a non-null, human-readable English message; may be
     *         overridden by the consumer's localized message catalog.
     */
    String message();
}
