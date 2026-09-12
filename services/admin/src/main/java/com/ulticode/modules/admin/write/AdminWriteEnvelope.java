package com.ulticode.modules.admin.write;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.auth.AdminActors;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.TraceIdUtil;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Producer-side metadata for an admin command sent to an owning service. */
public record AdminWriteEnvelope(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace) {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 120;

    /**
     * Builds the common owner-write metadata while leaving command business
     * fields and audit payloads with the calling admin writer.
     */
    public static AdminWriteEnvelope envelope(
            String operation,
            String requestedKey,
            String actorId,
            CurrentUserProvider currentUserProvider,
            String rationale) {
        if (actorId == null || actorId.isBlank()) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED,
                    "Authenticated admin actor is required");
        }
        if (operation == null || operation.isBlank()) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST,
                    "Owner write operation is required");
        }

        IdMetadata idempotency = idempotency(requestedKey);
        ActorDelegation actor = new ActorDelegation(
                AdminActors.typeOf(currentUserProvider), actorId, actorId, rationale);
        return new AdminWriteEnvelope(
                commandId(operation, idempotency),
                idempotency,
                actor,
                traceMetadata());
    }

    private static IdMetadata idempotency(String requestedKey) {
        if (requestedKey == null || requestedKey.isBlank()) {
            return IdMetadata.mint();
        }
        String key = requestedKey.trim();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST,
                    "Idempotency-Key must not exceed 120 characters");
        }
        return IdMetadata.of(key, null);
    }

    private static String commandId(String operation, IdMetadata idempotency) {
        return UUID.nameUUIDFromBytes(
                (operation + ":" + idempotency.idempotencyKey())
                        .getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private static TraceMetadata traceMetadata() {
        String traceId = TraceIdUtil.current();
        if (traceId == null || traceId.isBlank()) {
            traceId = "t-" + UUID.randomUUID();
        }
        return new TraceMetadata(traceId, null, null, null);
    }
}
