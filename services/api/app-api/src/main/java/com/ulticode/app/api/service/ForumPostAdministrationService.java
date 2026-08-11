package com.ulticode.app.api.service;

import com.ulticode.app.api.command.ForumPostModerationCommand;
import com.ulticode.app.api.dto.ForumPostModerationResultDTO;
import com.ulticode.common.rpc.RpcResult;

/**
 * App-owned forum-post moderation command boundary.
 *
 * <p>Only typed commands cross Dubbo; entity and mapper types remain inside
 * the App owner. The provider owns authorization, idempotency, and the local
 * mutation transaction.
 */
public interface ForumPostAdministrationService {

    RpcResult<ForumPostModerationResultDTO> moderate(ForumPostModerationCommand command);
}
