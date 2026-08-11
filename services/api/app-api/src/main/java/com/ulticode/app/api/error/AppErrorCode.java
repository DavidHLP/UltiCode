package com.ulticode.app.api.error;

import com.ulticode.common.error.NamespacedErrorCode;

/**
 * Namespaced error codes for {@code backend-app} RPC calls.
 *
 * <p>The namespace string {@code "app"} is what consumers see in the
 * {@link com.ulticode.common.rpc.RpcResult.ErrorPayload} when an RPC
 * fails. Codes are local to this namespace.
 *
 * <p>All App errors inherit the {@code "app"} namespace via
 * {@link #namespace()}. Generic transport/state codes are complemented by
 * legacy-compatible module codes where a consumer must preserve a domain
 * distinction across an owner boundary.
 */
public enum AppErrorCode implements NamespacedErrorCode {

    /** Generic malformed command or invalid state transition input. */
    BAD_REQUEST(40000, "Bad request"),

    /** Caller is not authenticated. */
    UNAUTHORIZED(40100, "Unauthorized"),

    /** Caller is authenticated but lacks the contest mutation permission. */
    FORBIDDEN(40300, "Forbidden"),

    /** Addressed content id (problem / submission / forum / solution) does not exist. */
    CONTENT_NOT_FOUND(40401, "Content not found"),

    /** Problem-owned item referenced by an App administration mutation is missing. */
    PROBLEM_NOT_FOUND(30001, "Problem not found"),

    /** A problem is already present in the addressed problem list. */
    PROBLEM_LIST_PROBLEM_DUPLICATE(90004, "This problem is already in the list"),
    /** Optimistic-lock conflict on a versioned App aggregate (problem version, etc.). */
    VERSION_CONFLICT(40901, "Version conflict"),

    /** Content is already published / closed and cannot accept the requested state change. */
    CONTENT_STATE_CONFLICT(40902, "Content state conflict"),

    /** Same idempotency key reused with a different request payload (fingerprint mismatch). */
    IDEMPOTENCY_KEY_CONFLICT(40903, "Idempotency key conflict"),

    /** Forum tag name already exists (CREATE / UPDATE). */
    FORUM_TAG_NAME_CONFLICT(40904, "Forum tag name already exists"),

    /** Forum tag slug already exists (CREATE / UPDATE). */
    FORUM_TAG_SLUG_CONFLICT(40905, "Forum tag slug already exists"),

    /** Generic unexpected app state; provider logged the underlying cause. */
    UNEXPECTED_APP_STATE(50001, "Unexpected app state");

    public static final String NAMESPACE = "app";

    private final int code;
    private final String message;

    AppErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String namespace() {
        return NAMESPACE;
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