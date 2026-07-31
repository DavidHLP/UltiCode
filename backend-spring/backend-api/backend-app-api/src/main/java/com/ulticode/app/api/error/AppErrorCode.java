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
 * {@link #namespace()}. New module-specific codes continue from
 * {@link #UNEXPECTED_APP_STATE} upward; reserved gaps preserve room
 * for future expansion.
 */
public enum AppErrorCode implements NamespacedErrorCode {

    /** Addressed content id (problem / submission / forum / solution) does not exist. */
    CONTENT_NOT_FOUND(40401, "Content not found"),

    /** Optimistic-lock conflict on a versioned App aggregate (problem version, etc.). */
    VERSION_CONFLICT(40901, "Version conflict"),

    /** Content is already published / closed and cannot accept the requested state change. */
    CONTENT_STATE_CONFLICT(40902, "Content state conflict"),

    /** Same idempotency key reused with a different request payload (fingerprint mismatch). */
    IDEMPOTENCY_KEY_CONFLICT(40903, "Idempotency key conflict"),

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