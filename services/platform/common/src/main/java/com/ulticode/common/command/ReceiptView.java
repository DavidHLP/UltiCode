package com.ulticode.common.command;

import java.io.Serializable;

/** Immutable receipt data returned by an owner store lookup. */
public record ReceiptView(
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
        String traceId) implements Serializable {
    private static final long serialVersionUID = 1L;
}
