package com.ulticode.common.command;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Immutable receipt data supplied by the common executor to an owner store. */
public record ReceiptWrite(
        String id,
        String commandId,
        String service,
        String operation,
        String idempotencyKey,
        String requestFingerprint,
        String status,
        String resultPayload,
        String actorType,
        String actorId,
        String traceId,
        LocalDateTime createdAt) implements Serializable {
    private static final long serialVersionUID = 1L;
}
