package com.ulticode.app.api.service;

import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;
import com.ulticode.common.rpc.RpcResult;

/**
 * App-owned administrative provider for notification/announcement
 * lifecycle operations.
 *
 * <p>Listed in {@code docs/MICROSERVICE_MIGRATION_GUIDE.md} &sect;4.3
 * as part of {@code backend-app}'s Dubbo providers. The
 * {@code notifications} and {@code contest_announcements} tables are
 * App-owned per {@code TABLE_OWNERS.md}; the Admin BFF must route
 * writes through this contract so that App is the sole writer.
 *
 * Three write methods mirror the Admin BFF's
 * {@code AdminNotificationService} interface. The App provider delegates
 * through its owner-local domain service and write port to the
 * {@code AnnouncementBroadcaster} seam for recipient resolution,
 * preference filtering, and batch row insert.
 *
 * <p>This interface is contract-only; no ServiceImpl lives in this
 * module. The provider implementation belongs to {@code backend-app}.
 */
public interface NotificationAdministrationService {

    /**
     * Create a system notification/announcement and deliver it to the
     * target audience. The provider resolves recipients, applies
     * preference filtering, batch-inserts notification rows, and fans
     * out via the announcement broadcaster.
     *
     * @param command carries commandId, idempotency, actor, trace,
     *                and the notification fields (title, content, type,
     *                category, target, userIds)
     * @return success with the representative {@link NotificationAdminViewDTO};
     *         failure with {@code BAD_REQUEST} when target=USERS but
     *         userIds is empty
     */
    RpcResult<NotificationAdminViewDTO> createNotification(CreateNotificationCommand command);

    /**
     * Delete a system notification and all its user copies.
     *
     * @param command carries commandId, idempotency, actor, trace,
     *                and the notificationId
     * @return success (void); failure with {@code CONTENT_NOT_FOUND}
     *         when the notification id is unknown
     */
    RpcResult<Void> deleteNotification(DeleteNotificationCommand command);

    /**
     * Update a system notification and all its user copies.
     *
     * @param command carries commandId, idempotency, actor, trace,
     *                the notificationId, and the new field values
     * @return success with the updated {@link NotificationAdminViewDTO};
     *         failure with {@code CONTENT_NOT_FOUND} when unknown
     */
    RpcResult<NotificationAdminViewDTO> updateNotification(UpdateNotificationCommand command);
}
