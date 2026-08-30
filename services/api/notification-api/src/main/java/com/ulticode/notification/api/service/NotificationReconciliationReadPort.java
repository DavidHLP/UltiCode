package com.ulticode.notification.api.service;

import com.ulticode.notification.api.dto.NotificationUserReferenceCountDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bounded, owner-local Notification facts used by Admin reconciliation.
 *
 * <p>{@code createdSince == null} requests full history. A non-null timestamp
 * requests the inclusive incremental creation window. Both modes page by
 * account id so consumers never receive unbounded Notification rows or
 * import owner persistence types.
 */
public interface NotificationReconciliationReadPort {

    int MAX_PAGE_SIZE = 500;

    /**
     * Return at most {@code limit} grouped user references after the
     * exclusive account-id cursor.
     *
     * @param afterAccountId exclusive account-id cursor; empty for first page
     * @param createdSince inclusive creation watermark; null for full history
     * @param limit requested page size, bounded by {@link #MAX_PAGE_SIZE}
     * @return non-null, account-id ordered facts
     */
    List<NotificationUserReferenceCountDTO> findUserReferenceCounts(
            String afterAccountId,
            LocalDateTime createdSince,
            int limit);
}
