package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Queue-domain error codes preserved from the legacy HTTP contract.
 *
 * <p>Carries legacy integer codes (16xxxx range) forward so existing
 * consumers see no numeric change. HttpStatus is stored for the app-level
 * exception handler but is not part of the NamespacedErrorCode wire contract.
 *
 * <p>P7-INFRA-S2: extracted from legacy
 * {@code com.ulticode.common.exception.ErrorCode} when the queue family
 * relocated from backend-legacy to backend-app.
 */
public enum QueueErrorCode implements NamespacedErrorCode {

    QUEUE_NOT_FOUND(160001, "Queue not found",
            "queue", HttpStatus.NOT_FOUND),
    QUEUE_JOB_NOT_FOUND(160002, "Job not found",
            "queue", HttpStatus.NOT_FOUND),
    QUEUE_OPERATION_FAILED(160003, "Queue operation failed",
            "queue", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final String namespace;
    private final HttpStatus httpStatus;

    QueueErrorCode(int code, String message, String namespace, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.namespace = namespace;
        this.httpStatus = httpStatus;
    }

    @Override
    public int code() { return code; }

    @Override
    public String message() { return message; }

    @Override
    public String namespace() { return namespace; }

    public HttpStatus httpStatus() { return httpStatus; }
}
