package com.ulticode.common.error;

/**
 * Generic, transport-neutral error codes that every module's
 * {@link NamespacedErrorCode} enum should reuse for the protocol-level
 * failures (input validation, unauthorized, not found, internal error,
 * etc.) instead of re-inventing its own integer.
 *
 * <p>Each module picks a fixed {@code namespace()} (typically the lower-case
 * module name) and combines it with one of these shared codes. Module-specific
 * codes coexist in the module's own enum.
 *
 * <p>This is a true <b>base</b> &mdash; it implements {@link NamespacedErrorCode}
 * with an empty namespace, so to actually use it inside an RPC, a wrapper
 * adapter is needed (see the migration guide's namespaced error-code
 * requirements). Modules that already have an HTTP-flavoured
 * {@code ErrorCode} enum (such as legacy's
 * {@code com.ulticode.common.exception.ErrorCode}) keep that enum in-place
 * for HTTP mappings; this base type is the contract every other
 * {@code XxxErrorCode} enum implements for cross-service transport.
 *
 * <p>HTTP status mapping is intentionally not declared here; the calling
 * module owns that mapping so backend-common stays free of Spring
 * {@code HttpStatus} dependencies.
 */
public enum BaseErrorCode implements NamespacedErrorCode {

    /** Generic success marker. Reserved for symmetry; never used as an error. */
    SUCCESS(0, "success"),

    /** Module caught a malformed client request. */
    BAD_REQUEST(40000, "Bad request"),

    /** Module-level validation failure (subtype of BAD_REQUEST). */
    VALIDATION_FAILED(49999, "Validation failed"),

    /** Caller is not authenticated. */
    UNAUTHORIZED(40100, "Unauthorized"),

    /** Caller is authenticated but lacks the required permission. */
    FORBIDDEN(40300, "Forbidden"),

    /** The addressed resource is missing. */
    NOT_FOUND(40400, "Not found"),

    /** The addressed method / path is not allowed. */
    METHOD_NOT_ALLOWED(40500, "Method not allowed"),

    /** The current resource state forbids the requested operation. */
    CONFLICT(40900, "Conflict"),

    /** Caller has been rate limited; back off and retry. */
    TOO_MANY_REQUESTS(42900, "Too many requests"),

    /** Module-specific failure with no better classification. */
    UNKNOWN_ERROR(50000, "Unknown error"),

    /** Underlying database failure; details belong in module-level logs. */
    DATABASE_ERROR(50001, "Database error");

    private final int code;
    private final String message;

    BaseErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String namespace() {
        return ""; // reserved namespace; sub-enums override.
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
