package com.ulticode.notification.error;

import com.ulticode.common.error.NamespacedErrorCode;

/**
 * Namespaced error codes emitted by the backend-notification owner.
 *
 * <p>The numeric values preserve the existing RPC contract while the
 * namespace identifies Notification as the owning service.
 */
public enum NotificationErrorCode implements NamespacedErrorCode {

    BAD_REQUEST(40000, "Bad request"),
    UNAUTHORIZED(40100, "Unauthorized"),
    FORBIDDEN(40300, "Forbidden"),
    CONTENT_NOT_FOUND(40401, "Content not found"),
    VERSION_CONFLICT(40901, "Version conflict"),
    CONTENT_STATE_CONFLICT(40902, "Content state conflict"),
    IDEMPOTENCY_KEY_CONFLICT(40903, "Idempotency key conflict"),
    UNEXPECTED_NOTIFICATION_STATE(50001, "Unexpected notification state");

    public static final String NAMESPACE = "notification";

    private final int code;
    private final String message;

    NotificationErrorCode(int code, String message) {
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
