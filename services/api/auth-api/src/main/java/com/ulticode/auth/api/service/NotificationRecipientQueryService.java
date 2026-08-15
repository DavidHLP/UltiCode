package com.ulticode.auth.api.service;

import com.ulticode.auth.api.dto.AuthNotificationRecipientDTO;
import com.ulticode.common.rpc.RpcResult;

import java.util.List;
import java.util.Set;

/**
 * Auth-owned recipient lookup used by the App notification read provider.
 *
 * <p>This keeps email and governance predicates in Auth while allowing the
 * notification service to depend on App's focused read seam rather than
 * calling Auth directly or reading Auth tables.
 */
public interface NotificationRecipientQueryService {

    /**
     * Resolve known recipients. Unknown ids are omitted; a provider failure
     * is represented by a failed {@link RpcResult}.
     */
    RpcResult<List<AuthNotificationRecipientDTO>> findRecipients(Set<String> accountIds);
}
