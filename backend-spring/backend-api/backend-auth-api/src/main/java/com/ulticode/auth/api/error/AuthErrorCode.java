package com.ulticode.auth.api.error;

import com.ulticode.common.error.NamespacedErrorCode;

/**
 * Namespaced error codes for {@code backend-auth} RPC calls.
 *
 * <p>The namespace string {@code "auth"} is what consumers see in the
 * {@link com.ulticode.common.rpc.RpcResult.ErrorPayload} when an RPC
 * fails. Codes are local to this namespace and intentionally do not
 * collide with HTTP-only error catalogs in {@code backend-legacy}.
 *
 * <p>All Auth errors inherit the {@code "auth"} namespace via
 * {@link #namespace()} so a single switch in the consumer can dispatch
 * on the module without inspecting the integer. New module-specific
 * codes continue from {@link #UNEXPECTED_AUTH_STATE} upward; reserved
 * gaps preserve room for future expansion.
 */
public enum AuthErrorCode implements NamespacedErrorCode {

    /** Addressed account id does not exist on the auth provider. */
    ACCOUNT_NOT_FOUND(40401, "Account not found"),

    /** Account is soft-disabled and cannot accept state-changing RPC. */
    ACCOUNT_DISABLED(40901, "Account disabled"),

    /** Account is banned; consumer should surface a 403. */
    ACCOUNT_BANNED(40902, "Account banned"),

    /** Authorization snapshot optimistic-lock conflict (expected version stale). */
    AUTHORIZATION_VERSION_CONFLICT(40903, "Authorization version conflict"),

    /** Addressed role does not exist on the auth provider. */
    ROLE_NOT_FOUND(40404, "Role not found"),

    /** A username or email is already owned by another account. */
    ACCOUNT_ALREADY_EXISTS(40904, "Account already exists"),

    /** The same idempotency key was reused with a different request body. */
    IDEMPOTENCY_KEY_CONFLICT(40905, "Idempotency key conflict"),

    /** The account-management command contains an invalid business value. */
    INVALID_ACCOUNT_REQUEST(40001, "Invalid account request"),

    /** The supplied current password does not match the stored credential. */
    PASSWORD_MISMATCH(40002, "Password mismatch"),

    /** Generic unexpected auth state; provider logged the underlying cause. */
    UNEXPECTED_AUTH_STATE(50001, "Unexpected auth state");

    public static final String NAMESPACE = "auth";

    private final int code;
    private final String message;

    AuthErrorCode(int code, String message) {
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