package com.ulticode.app.api.service;

import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.common.rpc.RpcResult;

/**
 * App-owned content moderation provider.
 *
 * <p>Listed in {@code docs/architecture/modules.md} as one of
 * {@code backend-app}'s Dubbo providers. The interface is contract-only and
 * the transaction stays inside App's Owner boundary. The Admin / Moderation
 * service calls {@link #apply} to enforce a moderation decision on App-owned
 * content (forum, solution, comment, problem note).
 *
 * <p>This interface is contract-only; no ServiceImpl lives in this
 * module. The provider implementation belongs to {@code backend-app}.
 */
public interface ContentModerationService {

    /**
     * Apply a moderation decision to a content item owned by App.
     *
     * @param command carries commandId, idempotency key, actor
     *                delegation, trace metadata, the moderation
     *                case id, the target content id + type, the
     *                action and the rationale
     * @return success with the resulting
     *         {@link ModerationApplyResultDTO}; failure with
     *         {@code CONTENT_NOT_FOUND} when the content id is
     *         unknown or {@code CONTENT_STATE_CONFLICT} when the
     *         action is not legal from the current content state
     */
    RpcResult<ModerationApplyResultDTO> apply(ApplyModerationCommand command);
}