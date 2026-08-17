package com.ulticode.app.api.command;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

/**
 * App-owned command to set the avatar for an account.
 *
 * <p>The avatar file upload (multipart handling, storage) happens at the
 * HTTP layer. This command carries only the resulting storage URL/path
 * reference, establishing {@code backend-app} as the sole writer of the
 * {@code user_profiles.avatar} column.
 *
 * <p>Semantically distinct from {@link UpdateProfileCommand#avatar()}:
 * avatar upload is a dedicated operation with its own idempotency key
 * and audit trail, supporting future image-processing extensions
 * (thumbnails, compression) without coupling to the general profile
 * update path.
 *
 * @param commandId   stable command id for log correlation and dedup
 * @param idempotency idempotency metadata for provider-side replay-dedup
 * @param actor       who initiated the write
 * @param trace       trace metadata for deadline propagation
 * @param accountId   target account (FK to auth.users.id)
 * @param avatarUrl   storage URL or path of the uploaded avatar
 */
public record UploadAvatarCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String accountId,
        String avatarUrl) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    /**
     * Compact constructor with boundary validation.
     *
     * @throws IllegalArgumentException if accountId is blank or avatarUrl
     *         exceeds 255 characters (matching the DDL column constraint)
     */
    public UploadAvatarCommand {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId is required");
        }
        if (avatarUrl == null || avatarUrl.isBlank()) {
            throw new IllegalArgumentException("avatarUrl is required");
        }
        if (avatarUrl.length() > 255) {
            throw new IllegalArgumentException("avatarUrl must not exceed 255 characters");
        }
    }

    @Override
    public String commandId() {
        return commandId;
    }

    @Override
    public IdMetadata idempotency() {
        return idempotency;
    }

    @Override
    public ActorDelegation actor() {
        return actor;
    }

    @Override
    public TraceMetadata trace() {
        return trace;
    }
}
