package com.ulticode.app.api.service;

import com.ulticode.app.api.command.ForumTagMutationCommand;
import com.ulticode.app.api.dto.ForumTagDTO;
import com.ulticode.common.rpc.RpcResult;

/**
 * ADMIN-007: app-owned administrative provider for forum tag lifecycle
 * writes (create / update / delete / merge).
 *
 * <p>Consumed by the Admin service's {@code ForumTagHandler}, which
 * previously reached for {@code ForumTagMapper} directly. Every call is
 * a mutating RPC, so the command carries the full
 * {@code commandId / idempotency / actor / trace} metadata per the
 * {@link com.ulticode.common.rpc.RpcPolicy} write boundary. Name / slug
 * conflict detection lives here (provider side, race-free); the Admin
 * consumer maps the namespaced failure codes onto its own error codes.
 *
 * @author ulticode
 */
public interface ForumTagAdministrationService {

    /**
     * Apply a single forum-tag mutation.
     *
     * @param command the mutation command (action + payload + actor
     *                metadata)
     * @return success with the resulting tag row (the deleted tag snapshot
     *         for {@code DELETE}, the surviving target tag for {@code MERGE});
     *         failure with {@code CONTENT_NOT_FOUND} when an addressed
     *         tag is unknown, {@code FORUM_TAG_NAME_CONFLICT} or
     *         {@code FORUM_TAG_SLUG_CONFLICT} on CREATE / UPDATE
     */
    RpcResult<ForumTagDTO> mutate(ForumTagMutationCommand command);
}
